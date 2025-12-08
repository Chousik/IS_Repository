import { FormEvent, useCallback, useEffect, useState } from 'react';
import { useToast } from '../components/ToastProvider';
import { formatDateTime } from '../utils/strings';
import { subscribeToEntityChanges, EntityChange } from '../services/events';

interface ImportJobResponse {
  id: string;
  entityType: string;
  status: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  filename: string;
  contentType?: string;
  fileSize?: number;
  downloadUrl?: string;
  totalRecords?: number;
  successCount?: number;
  errorMessage?: string;
  createdAt: string;
  finishedAt?: string;
}

const ImportPage = () => {
  const { showToast } = useToast();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [jobs, setJobs] = useState<ImportJobResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  interface ErrorDetails {
    status: number;
    rawMessage?: string;
  }

  const extractErrorDetails = useCallback(async (response: Response): Promise<ErrorDetails> => {
    let rawMessage: string | undefined;
    try {
      const data = await response.json();
      rawMessage = data?.message ?? data?.error ?? undefined;
    } catch (jsonError) {
      // ignore JSON parse issues
    }
    if (!rawMessage) {
      try {
        const text = await response.text();
        rawMessage = text || undefined;
      } catch (textError) {
        rawMessage = undefined;
      }
    }
    return { status: response.status, rawMessage };
  }, []);

  const buildFriendlyError = useCallback((rawMessage?: string, status?: number): string => {
    const normalized = rawMessage?.toLowerCase() ?? '';
    if (!rawMessage && (status === undefined || status === 0)) {
      return 'Сервер импорта недоступен. Проверьте, запущены ли БД и файловое хранилище.';
    }
    if (normalized.includes('файл') || normalized.includes('minio') || normalized.includes('хранилищ') || normalized.includes('storage')) {
      return `Ошибка файлового хранилища (MinIO). ${rawMessage ?? 'Файл не был сохранён.'}`;
    }
    if (status && status >= 500) {
      return `Ошибка сервера или базы данных (HTTP ${status}). ${rawMessage ?? 'Повторите попытку позже.'}`;
    }
    return rawMessage ?? 'Не удалось выполнить операцию';
  }, []);

  const formatCaughtError = useCallback(
    (error: any) => {
      if (error?.friendlyMessage) {
        return error.friendlyMessage as string;
      }
      const message = error?.message as string | undefined;
      if (!message) {
        return 'Произошла неизвестная ошибка';
      }
      const lowered = message.toLowerCase();
      if (lowered.includes('failed to fetch') || lowered.includes('network')) {
        return buildFriendlyError(undefined, undefined);
      }
      return message;
    },
    [buildFriendlyError],
  );

  const fetchHistory = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/v1/imports/study-groups');
      if (!response.ok) {
        const details = await extractErrorDetails(response);
        const friendlyMessage = buildFriendlyError(details.rawMessage, details.status);
        throw { friendlyMessage };
      }
      const data: ImportJobResponse[] = await response.json();
      setJobs(data);
    } catch (error: any) {
      showToast(formatCaughtError(error) ?? 'Не удалось загрузить историю импорта', 'error');
    } finally {
      setLoading(false);
    }
  }, [buildFriendlyError, extractErrorDetails, formatCaughtError, showToast]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  useEffect(() => {
    const unsubscribe = subscribeToEntityChanges((change: EntityChange) => {
      if (change.entity !== 'IMPORT_JOB') {
        return;
      }
      const job = change.data as ImportJobResponse;
      setJobs((current) => {
        const without = current.filter((item) => item.id !== job.id);
        const next = [job, ...without];
        next.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        return next;
      });
    });
    return unsubscribe;
  }, []);

  const handleUpload = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedFile) {
      showToast('Выберите YAML-файл для загрузки', 'warning');
      return;
    }
    const formData = new FormData();
    formData.append('file', selectedFile);
    setUploading(true);
    try {
      const response = await fetch('/api/v1/imports/study-groups', {
        method: 'POST',
        body: formData,
      });
      if (!response.ok) {
        const details = await extractErrorDetails(response);
        const friendlyMessage = buildFriendlyError(details.rawMessage, details.status);
        throw { friendlyMessage };
      }
      showToast('Импорт выполнен успешно', 'success');
      setSelectedFile(null);
      await fetchHistory();
    } catch (error: any) {
      showToast(formatCaughtError(error) ?? 'Не удалось выполнить импорт', 'error');
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = useCallback(
    async (job: ImportJobResponse) => {
      if (!job.downloadUrl) {
        showToast('Файл ещё не готов или недоступен для скачивания', 'warning');
        return;
      }
      setDownloadingId(job.id);
      try {
        const response = await fetch(job.downloadUrl);
        if (!response.ok) {
          const details = await extractErrorDetails(response);
          const friendlyMessage = buildFriendlyError(details.rawMessage, details.status);
          throw { friendlyMessage };
        }
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = job.filename || 'import-file.yaml';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
      } catch (error: any) {
        showToast(formatCaughtError(error) ?? 'Не удалось скачать файл импорта', 'error');
      } finally {
        setDownloadingId(null);
      }
    },
    [buildFriendlyError, extractErrorDetails, formatCaughtError, showToast],
  );

  return (
    <div>
      <div className="section-heading">
        <h2>Импорт учебных групп</h2>
      </div>

      <form className="form-grid" onSubmit={handleUpload}>
        <div className="form-field full-width">
          <label>YAML-файл с группами</label>
          <input
            type="file"
            accept=".yaml,.yml"
            onChange={(event) => {
              const file = event.target.files?.[0];
              setSelectedFile(file ?? null);
            }}
          />
        </div>
        <div className="form-field" style={{ alignSelf: 'flex-end' }}>
          <button type="submit" className="primary-btn" disabled={uploading}>
            {uploading ? 'Импорт...' : 'Импортировать'}
          </button>
        </div>
      </form>

      <div className="table-wrapper" style={{ marginTop: 24 }}>
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Файл</th>
              <th>Статус</th>
              <th>Добавлено</th>
              <th>Создано</th>
              <th>Завершено</th>
              <th>Ошибка</th>
            </tr>
          </thead>
          <tbody>
            {jobs.length === 0 ? (
              <tr>
                <td colSpan={8}>{loading ? 'Загрузка...' : 'История пуста'}</td>
              </tr>
            ) : (
              jobs.map((job) => (
                <tr key={job.id}>
                  <td>{job.id}</td>
                  <td>
                    <div>{job.filename}</div>
                    {job.downloadUrl && (
                      <button
                        type="button"
                        className="secondary-btn"
                        onClick={() => handleDownload(job)}
                        disabled={downloadingId === job.id}
                      >
                        {downloadingId === job.id ? 'Скачивание...' : 'Скачать'}
                      </button>
                    )}
                  </td>
                  <td>{job.status}</td>
                  <td>{job.status === 'COMPLETED' ? job.successCount ?? job.totalRecords ?? '—' : '—'}</td>
                  <td>{formatDateTime(job.createdAt)}</td>
                  <td>{job.finishedAt ? formatDateTime(job.finishedAt) : '—'}</td>
                  <td>{job.status === 'FAILED' ? job.errorMessage ?? 'Ошибка' : '—'}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default ImportPage;
