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

  const extractErrorMessage = useCallback(async (response: Response): Promise<string> => {
    try {
      const data = await response.json();
      if (data?.message) return data.message;
      if (data?.error) return data.error;
    } catch (jsonError) {
      // ignore and fall through to text
    }
    try {
      const text = await response.text();
      if (text) return text;
    } catch (textError) {
      // ignore
    }
    return response.statusText || 'Неизвестная ошибка';
  }, []);

  const fetchHistory = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/v1/imports/study-groups');
      if (!response.ok) {
        const message = await extractErrorMessage(response);
        throw new Error(message);
      }
      const data: ImportJobResponse[] = await response.json();
      setJobs(data);
    } catch (error: any) {
      showToast(error?.message ?? 'Не удалось загрузить историю импорта', 'error');
    } finally {
      setLoading(false);
    }
  }, [extractErrorMessage, showToast]);

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
        const message = await extractErrorMessage(response);
        throw new Error(message || 'Не удалось выполнить импорт');
      }
      showToast('Импорт выполнен успешно', 'success');
      setSelectedFile(null);
      await fetchHistory();
    } catch (error: any) {
      showToast(error?.message ?? 'Не удалось выполнить импорт', 'error');
    } finally {
      setUploading(false);
    }
  };

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
                      <a
                        className="secondary-btn"
                        href={job.downloadUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        Скачать
                      </a>
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
