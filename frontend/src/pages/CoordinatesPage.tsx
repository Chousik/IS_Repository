import { FormEvent, useEffect, useMemo, useState } from 'react';
import { coordinatesApi, studyGroupsApi } from '../apiClient';
import type {
  CoordinatesAddRequest,
  CoordinatesResponse,
  CoordinatesUpdateRequest,
  StudyGroupResponse,
} from '../api/models';
import { loadAllCoordinates, loadAllStudyGroups } from '../services/entityLoaders';
import Modal from '../components/Modal';

const PAGE_SIZE = 10;

const CoordinatesPage = () => {
  const [coordinates, setCoordinates] = useState<CoordinatesResponse[]>([]);
  const [studyGroups, setStudyGroups] = useState<StudyGroupResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [createOpen, setCreateOpen] = useState(false);
  const [editCoordinate, setEditCoordinate] = useState<CoordinatesResponse | null>(null);
  const [deleteContext, setDeleteContext] = useState<{ coordinate: CoordinatesResponse; groupIds: number[] } | null>(null);
  const [replacementId, setReplacementId] = useState<number | ''>('');
  const [error, setError] = useState<string | null>(null);

  const refreshData = async () => {
    try {
      setLoading(true);
      const [coords, groups] = await Promise.all([loadAllCoordinates(), loadAllStudyGroups()]);
      setCoordinates(coords);
      setStudyGroups(groups);
      setError(null);
    } catch (err: any) {
      setError(err?.message ?? 'Не удалось загрузить координаты');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refreshData();
    const interval = setInterval(refreshData, 7000);
    return () => clearInterval(interval);
  }, []);

  const maxPage = Math.max(1, Math.ceil(coordinates.length / PAGE_SIZE));
  const currentPage = Math.min(page, maxPage);
  const paginated = coordinates.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

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
      await refreshData();
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось создать координаты');
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
      await refreshData();
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось обновить координаты');
    }
  };

  const handleDelete = (coordinate: CoordinatesResponse) => {
    const affected = studyGroups.filter((group) => group.coordinates?.id === coordinate.id).map((g) => g.id!).filter(Boolean);
    if (affected.length === 0) {
      if (window.confirm('Удалить координаты?')) {
        coordinatesApi
          .apiV1CoordinatesIdDelete({ id: coordinate.id! })
          .then(refreshData)
          .catch((err) => alert(err?.message ?? 'Не удалось удалить координаты'));
      }
      return;
    }
    setDeleteContext({ coordinate, groupIds: affected });
    setReplacementId('');
  };

  const confirmDeleteWithReplacement = async () => {
    if (!deleteContext?.coordinate.id || replacementId === '') {
      alert('Выберите координаты для переназначения');
      return;
    }
    try {
      await Promise.all(
        deleteContext.groupIds.map((id) =>
          studyGroupsApi.apiV1StudyGroupsIdPatch({
            id,
            studyGroupUpdateRequest: { coordinatesId: Number(replacementId) },
          })
        )
      );
      await coordinatesApi.apiV1CoordinatesIdDelete({ id: deleteContext.coordinate.id });
      setDeleteContext(null);
      setReplacementId('');
      await refreshData();
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось переназначить координаты');
    }
  };

  const replacementOptions = useMemo(
    () =>
      (deleteContext?.coordinate
        ? coordinates.filter((coord) => coord.id !== deleteContext.coordinate.id)
        : coordinates
      ).map((coord) => ({ label: `#${coord.id} (${coord.x}; ${coord.y})`, value: coord.id! })),
    [coordinates, deleteContext]
  );

  return (
    <div>
      <div className="section-heading">
        <h2>Координаты</h2>
        <button className="primary-btn" onClick={() => setCreateOpen(true)}>Добавить</button>
      </div>

      {error && <div style={{ color: '#dc2626', marginBottom: 12 }}>{error}</div>}

      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>X</th>
              <th>Y</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={4}>Загрузка...</td>
              </tr>
            ) : paginated.length === 0 ? (
              <tr>
                <td colSpan={4}>Нет данных</td>
              </tr>
            ) : (
              paginated.map((coord) => (
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
        <button className="secondary-btn" onClick={() => setPage((prev) => Math.max(1, prev - 1))} disabled={currentPage === 1}>
          Назад
        </button>
        <span>Страница {currentPage} из {maxPage}</span>
        <button className="secondary-btn" onClick={() => setPage((prev) => Math.min(maxPage, prev + 1))} disabled={currentPage === maxPage}>
          Вперёд
        </button>
      </div>

      <Modal
        open={createOpen}
        title="Создание координат"
        onClose={() => setCreateOpen(false)}
        footer={null}
      >
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
      </Modal>

      <Modal
        open={!!editCoordinate}
        title="Редактирование координат"
        onClose={() => setEditCoordinate(null)}
        footer={null}
      >
        <form className="form-grid" onSubmit={handleUpdate}>
          <div className="form-field">
            <label>X</label>
            <input
              className="number-input"
              name="x"
              type="number"
              defaultValue={editCoordinate?.x}
              required
            />
          </div>
          <div className="form-field">
            <label>Y</label>
            <input
              className="number-input"
              name="y"
              type="number"
              step="0.1"
              defaultValue={editCoordinate?.y}
              required
            />
          </div>
          <div className="modal-footer">
            <button type="button" className="secondary-btn" onClick={() => setEditCoordinate(null)}>Отмена</button>
            <button type="submit" className="primary-btn">Сохранить</button>
          </div>
        </form>
      </Modal>

      <Modal
        open={!!deleteContext}
        title="Переназначение координат"
        onClose={() => {
          setDeleteContext(null);
          setReplacementId('');
        }}
        footer={null}
      >
        {deleteContext && (
          <form className="form-grid" onSubmit={(event) => { event.preventDefault(); confirmDeleteWithReplacement(); }}>
            <p>
              Координаты используются в учебных группах. Выберите другие координаты, чтобы переназначить {deleteContext.groupIds.length} групп(ы).
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
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="modal-footer">
              <button type="button" className="secondary-btn" onClick={() => setDeleteContext(null)}>Отмена</button>
              <button type="submit" className="danger-btn">Переназначить и удалить</button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
};

export default CoordinatesPage;
