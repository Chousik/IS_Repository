import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import {
  CoordinatesAddRequest,
  CoordinatesResponse,
  CoordinatesUpdateRequest,
  StudyGroupResponse,
} from '../api/models';
import { coordinatesApi, studyGroupsApi } from '../apiClient';
import { mapPageModel, PagedResult } from '../utils/pagination';
import { subscribeToEntityChanges, EntityChange } from '../services/events';
import { loadAllCoordinates, loadAllStudyGroups } from '../services/entityLoaders';
import Modal from '../components/Modal';
import { useToast } from '../components/ToastProvider';

const PAGE_SIZE = 10;

type SortOrder = 'asc' | 'desc';

type PagingState = {
  page: number;
  size: number;
  sortField?: string;
  sortOrder: SortOrder;
};

const initialPage: PagedResult<CoordinatesResponse> = {
  content: [],
  page: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
};

const CoordinatesPage = () => {
  const [paging, setPaging] = useState<PagingState>({ page: 1, size: PAGE_SIZE, sortField: undefined, sortOrder: 'asc' });
  const [pagedData, setPagedData] = useState<PagedResult<CoordinatesResponse>>(initialPage);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [studyGroups, setStudyGroups] = useState<StudyGroupResponse[]>([]);
  const [allCoordinates, setAllCoordinates] = useState<CoordinatesResponse[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [editCoordinate, setEditCoordinate] = useState<CoordinatesResponse | null>(null);
  const [deleteContext, setDeleteContext] = useState<{ coordinate: CoordinatesResponse; groupIds: number[] } | null>(null);
  const [replacementId, setReplacementId] = useState<number | ''>('');
  const [confirmDelete, setConfirmDelete] = useState<CoordinatesResponse | null>(null);
  const { showToast } = useToast();

  const fetchPage = useCallback(async () => {
    setLoading(true);
    try {
      const response = await coordinatesApi.apiV1CoordinatesGet({
        page: paging.page - 1,
        size: paging.size,
        sortBy: paging.sortField,
        direction: paging.sortField ? paging.sortOrder : undefined,
      });
      setPagedData(mapPageModel<CoordinatesResponse>(response, paging.size));
      setError(null);
    } catch (err: any) {
      const message = err?.message ?? 'Не удалось загрузить координаты';
      setError(message);
      showToast(message, 'error');
    } finally {
      setLoading(false);
    }
  }, [paging.page, paging.size, paging.sortField, paging.sortOrder, showToast]);

  const fetchStudyGroups = useCallback(async () => {
    try {
      const [groups, coords] = await Promise.all([
        loadAllStudyGroups(),
        loadAllCoordinates(),
      ]);
      setStudyGroups(groups);
      setAllCoordinates(coords);
    } catch (err) {
      console.warn('Не удалось загрузить учебные группы', err);
    }
  }, []);

  useEffect(() => {
    fetchPage();
  }, [fetchPage]);

  useEffect(() => {
    fetchStudyGroups();
  }, [fetchStudyGroups]);

  useEffect(() => {
    const unsubscribe = subscribeToEntityChanges((change: EntityChange) => {
      if (change.entity === 'COORDINATES') {
        fetchPage();
        fetchStudyGroups();
      }
      if (change.entity === 'STUDY_GROUP') {
        fetchStudyGroups();
      }
    });
    return unsubscribe;
  }, [fetchPage, fetchStudyGroups]);

  const maxPage = pagedData.totalPages > 0 ? pagedData.totalPages : 1;

  const toggleSort = (field: string) => {
    setPaging((prev) => {
      if (prev.sortField !== field) {
        return { ...prev, page: 1, sortField: field, sortOrder: 'asc' };
      }
      if (prev.sortOrder === 'asc') {
        return { ...prev, page: 1, sortField: field, sortOrder: 'desc' };
      }
      return { ...prev, page: 1, sortField: undefined, sortOrder: 'asc' };
    });
  };

  const sortIcon = (field: string) => {
    if (paging.sortField !== field) return '';
    return paging.sortOrder === 'asc' ? '▲' : '▼';
  };

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const payload: CoordinatesAddRequest = {
      x: Number(formData.get('x')),
      y: Number(formData.get('y')),
    };
    try {
      await coordinatesApi.apiV1CoordinatesPost({ coordinatesAddRequest: payload });
      setCreateOpen(false);
      await fetchPage();
      showToast('Координаты созданы', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось создать координаты', 'error');
    }
  };

  const handleUpdate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editCoordinate?.id) return;
    const formData = new FormData(event.currentTarget);
    const payload: CoordinatesUpdateRequest = {
      x: Number(formData.get('x')),
      y: Number(formData.get('y')),
    };
    try {
      await coordinatesApi.apiV1CoordinatesIdPatch({ id: editCoordinate.id, coordinatesUpdateRequest: payload });
      setEditCoordinate(null);
      await fetchPage();
      showToast('Координаты обновлены', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось обновить координаты', 'error');
    }
  };

  const handleDelete = (coordinate: CoordinatesResponse) => {
    if (!coordinate.id) return;
    const affected = studyGroups.filter((group) => group.coordinates?.id === coordinate.id)
      .map((g) => g.id!)
      .filter(Boolean);
    if (affected.length === 0) {
      setConfirmDelete(coordinate);
      return;
    }
    setDeleteContext({ coordinate, groupIds: affected });
    setReplacementId('');
  };

  const confirmDeleteWithReplacement = async () => {
    if (!deleteContext?.coordinate.id || replacementId === '') {
      showToast('Выберите координаты для переназначения', 'warning');
      return;
    }
    try {
      await Promise.all(
        deleteContext.groupIds.map((id) =>
          studyGroupsApi.apiV1StudyGroupsIdPatch({ id, studyGroupUpdateRequest: { coordinatesId: Number(replacementId) } })
        )
      );
      await coordinatesApi.apiV1CoordinatesIdDelete({ id: deleteContext.coordinate.id });
      setDeleteContext(null);
      setReplacementId('');
      await fetchPage();
      showToast('Координаты переназначены и удалены', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось переназначить координаты', 'error');
    }
  };

  const replacementOptions = useMemo(() => {
    const unique = new Map<number, CoordinatesResponse>();
    for (const coord of allCoordinates) {
      if (coord?.id != null) {
        unique.set(coord.id, coord);
      }
    }
    if (deleteContext?.coordinate?.id != null) {
      unique.delete(deleteContext.coordinate.id);
    }
    return Array.from(unique.values()).map((coord) => ({ label: `#${coord.id} (${coord.x}; ${coord.y})`, value: coord.id! }));
  }, [allCoordinates, deleteContext]);

  const confirmDeleteCoordinate = async () => {
    if (!confirmDelete?.id) {
      setConfirmDelete(null);
      return;
    }
    try {
      await coordinatesApi.apiV1CoordinatesIdDelete({ id: confirmDelete.id });
      await fetchPage();
      showToast('Координаты удалены', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось удалить координаты', 'error');
    } finally {
      setConfirmDelete(null);
    }
  };

  return (
    <div>
      <div className="section-heading">
        <h2>Координаты</h2>
        <button className="primary-btn" onClick={() => setCreateOpen(true)}>Добавить</button>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              <th onClick={() => toggleSort('id')} style={{ cursor: 'pointer' }}>ID {sortIcon('id')}</th>
              <th onClick={() => toggleSort('x')} style={{ cursor: 'pointer' }}>X {sortIcon('x')}</th>
              <th onClick={() => toggleSort('y')} style={{ cursor: 'pointer' }}>Y {sortIcon('y')}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={4}>Загрузка...</td></tr>
            ) : pagedData.content.length === 0 ? (
              <tr><td colSpan={4}>Нет данных</td></tr>
            ) : (
              pagedData.content.map((coord) => (
                <tr key={coord.id}>
                  <td>{coord.id}</td>
                  <td>{coord.x}</td>
                  <td>{coord.y}</td>
                  <td>
                    <div className="form-inline">
                      <button className="secondary-btn" onClick={() => setEditCoordinate(coord)}>Изменить</button>
                      <button className="danger-btn" onClick={() => handleDelete(coord)}>Удалить</button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
        <button className="secondary-btn" onClick={() => setPaging((prev) => ({ ...prev, page: Math.max(1, prev.page - 1) }))} disabled={paging.page === 1}>
          Назад
        </button>
        <span>Страница {paging.page} из {maxPage}</span>
        <button className="secondary-btn" onClick={() => setPaging((prev) => ({ ...prev, page: Math.min(prev.page + 1, maxPage) }))} disabled={paging.page >= maxPage}>
          Вперёд
        </button>
      </div>

      <div className="modal-overlay" style={{ display: createOpen ? 'flex' : 'none' }} onClick={() => setCreateOpen(false)}>
        <div className="modal-body" onClick={(event) => event.stopPropagation()}>
          <h3>Создание координат</h3>
          <form className="form-grid" onSubmit={handleCreate}>
            <div className="form-field">
              <label>X</label>
              <input className="number-input" name="x" type="number" required />
            </div>
            <div className="form-field">
              <label>Y</label>
              <input className="number-input" name="y" type="number" step="0.1" required />
            </div>
            <div className="modal-footer">
              <button type="button" className="secondary-btn" onClick={() => setCreateOpen(false)}>Отмена</button>
              <button type="submit" className="primary-btn">Сохранить</button>
            </div>
          </form>
        </div>
      </div>

      <div className="modal-overlay" style={{ display: editCoordinate ? 'flex' : 'none' }} onClick={() => setEditCoordinate(null)}>
        <div className="modal-body" onClick={(event) => event.stopPropagation()}>
          <h3>Редактирование координат</h3>
          <form className="form-grid" onSubmit={handleUpdate}>
            <div className="form-field">
              <label>X</label>
              <input className="number-input" name="x" type="number" defaultValue={editCoordinate?.x} required />
            </div>
            <div className="form-field">
              <label>Y</label>
              <input className="number-input" name="y" type="number" step="0.1" defaultValue={editCoordinate?.y} required />
            </div>
            <div className="modal-footer">
              <button type="button" className="secondary-btn" onClick={() => setEditCoordinate(null)}>Отмена</button>
              <button type="submit" className="primary-btn">Сохранить</button>
            </div>
          </form>
        </div>
      </div>

      <div className="modal-overlay" style={{ display: deleteContext ? 'flex' : 'none' }} onClick={() => setDeleteContext(null)}>
        <div className="modal-body" onClick={(event) => event.stopPropagation()}>
          <h3>Переназначение координат</h3>
          {deleteContext && (
            <form
              className="form-grid single-column"
              onSubmit={(event) => {
                event.preventDefault();
                confirmDeleteWithReplacement();
              }}
            >
              <p>
                Координаты используются в {deleteContext.groupIds.length} учебных группах. Выберите другие координаты перед удалением.
              </p>
              <div className="form-field">
                <label>Новые координаты</label>
                <select
                  className="select"
                  value={replacementId}
                  onChange={(event) => setReplacementId(event.target.value ? Number(event.target.value) : '')}
                  required
                >
                  <option value="">Выберите координаты</option>
                  {replacementOptions.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </div>
              <div className="modal-footer">
                <button type="button" className="secondary-btn" onClick={() => setDeleteContext(null)}>Отмена</button>
                <button type="submit" className="danger-btn">Переназначить и удалить</button>
              </div>
            </form>
          )}
        </div>
      </div>

      <Modal
        open={!!confirmDelete}
        title="Удаление координат"
        onClose={() => setConfirmDelete(null)}
        footer={null}
      >
        {confirmDelete && (
          <form className="form-grid" onSubmit={(event) => { event.preventDefault(); confirmDeleteCoordinate(); }}>
            <p>Удалить координаты <strong>#{confirmDelete.id}</strong> ({confirmDelete.x}; {confirmDelete.y})?</p>
            <div className="modal-footer">
              <button type="button" className="secondary-btn" onClick={() => setConfirmDelete(null)}>Отмена</button>
              <button type="submit" className="danger-btn">Удалить</button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
};

export default CoordinatesPage;
