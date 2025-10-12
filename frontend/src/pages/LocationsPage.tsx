import { FormEvent, useEffect, useMemo, useState } from 'react';
import { locationsApi, personsApi } from '../apiClient';
import type {
  LocationAddRequest,
  LocationResponse,
  LocationUpdateRequest,
  PersonResponse,
} from '../api/models';
import { loadAllLocations, loadAllPersons } from '../services/entityLoaders';
import Modal from '../components/Modal';

const PAGE_SIZE = 10;

const LocationsPage = () => {
  const [locations, setLocations] = useState<LocationResponse[]>([]);
  const [persons, setPersons] = useState<PersonResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [createOpen, setCreateOpen] = useState(false);
  const [editLocation, setEditLocation] = useState<LocationResponse | null>(null);
  const [deleteContext, setDeleteContext] = useState<{ location: LocationResponse; personIds: number[] } | null>(null);
  const [replacementId, setReplacementId] = useState<number | ''>('');

  const refreshData = async () => {
    try {
      setLoading(true);
      const [locs, people] = await Promise.all([loadAllLocations(), loadAllPersons()]);
      setLocations(locs);
      setPersons(people);
      setError(null);
    } catch (err: any) {
      setError(err?.message ?? 'Не удалось загрузить локации');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refreshData();
    const interval = setInterval(refreshData, 7000);
    return () => clearInterval(interval);
  }, []);

  const maxPage = Math.max(1, Math.ceil(locations.length / PAGE_SIZE));
  const currentPage = Math.min(page, maxPage);
  const paginated = locations.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const payload: LocationAddRequest = {
      name: String(formData.get('name')),
      x: Number(formData.get('x')),
      y: Number(formData.get('y')),
      z: Number(formData.get('z')),
    };
    try {
      await locationsApi.apiV1LocationsPost({ locationAddRequest: payload });
      setCreateOpen(false);
      await refreshData();
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось создать локацию');
    }
  };

  const handleUpdate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editLocation?.id) return;
    const formData = new FormData(event.currentTarget);
    const payload: LocationUpdateRequest = {
      name: String(formData.get('name')),
      x: Number(formData.get('x')),
      y: Number(formData.get('y')),
      z: Number(formData.get('z')),
    };
    try {
      await locationsApi.apiV1LocationsIdPatch({ id: editLocation.id, locationUpdateRequest: payload });
      setEditLocation(null);
      await refreshData();
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось обновить локацию');
    }
  };

  const handleDelete = (location: LocationResponse) => {
    const affected = persons.filter((person) => person.location?.id === location.id).map((p) => p.id!).filter(Boolean);
    if (affected.length === 0) {
      if (window.confirm('Удалить локацию?')) {
        locationsApi
          .apiV1LocationsIdDelete({ id: location.id! })
          .then(refreshData)
          .catch((err) => alert(err?.message ?? 'Не удалось удалить локацию'));
      }
      return;
    }
    setDeleteContext({ location, personIds: affected });
    setReplacementId('');
  };

  const confirmDeleteWithReplacement = async () => {
    if (!deleteContext?.location.id || replacementId === '') {
      alert('Выберите локацию для переназначения');
      return;
    }
    try {
      await Promise.all(
        deleteContext.personIds.map((id) =>
          personsApi.apiV1PersonsIdPatch({
            id,
            personUpdateRequest: { locationId: Number(replacementId) },
          })
        )
      );
      await locationsApi.apiV1LocationsIdDelete({ id: deleteContext.location.id });
      setDeleteContext(null);
      setReplacementId('');
      await refreshData();
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось переназначить локации');
    }
  };

  const replacementOptions = useMemo(
    () =>
      (deleteContext?.location
        ? locations.filter((loc) => loc.id !== deleteContext.location.id)
        : locations
      ).map((loc) => ({ label: `#${loc.id} ${loc.name}`, value: loc.id! })),
    [locations, deleteContext]
  );

  return (
    <div>
      <div className="section-heading">
        <h2>Локации</h2>
        <button className="primary-btn" onClick={() => setCreateOpen(true)}>Добавить</button>
      </div>

      {error && <div style={{ color: '#dc2626', marginBottom: 12 }}>{error}</div>}

      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Название</th>
              <th>X</th>
              <th>Y</th>
              <th>Z</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6}>Загрузка...</td>
              </tr>
            ) : paginated.length === 0 ? (
              <tr>
                <td colSpan={6}>Нет данных</td>
              </tr>
            ) : (
              paginated.map((location) => (
                <tr key={location.id}>
                  <td>{location.id}</td>
                  <td>{location.name}</td>
                  <td>{location.x}</td>
                  <td>{location.y}</td>
                  <td>{location.z}</td>
                  <td>
                    <div className="form-inline">
                      <button className="secondary-btn" onClick={() => setEditLocation(location)}>Изменить</button>
                      <button className="danger-btn" onClick={() => handleDelete(location)}>Удалить</button>
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

      <Modal open={createOpen} title="Создание локации" onClose={() => setCreateOpen(false)} footer={null}>
        <form className="form-grid" onSubmit={handleCreate}>
          <div className="form-field">
            <label>Название</label>
            <input className="input" name="name" required />
          </div>
          <div className="form-field">
            <label>X</label>
            <input className="number-input" name="x" type="number" required />
          </div>
          <div className="form-field">
            <label>Y</label>
            <input className="number-input" name="y" type="number" required />
          </div>
          <div className="form-field">
            <label>Z</label>
            <input className="number-input" name="z" type="number" required />
          </div>
          <div className="modal-footer">
            <button type="button" className="secondary-btn" onClick={() => setCreateOpen(false)}>Отмена</button>
            <button type="submit" className="primary-btn">Сохранить</button>
          </div>
        </form>
      </Modal>

      <Modal open={!!editLocation} title="Редактирование локации" onClose={() => setEditLocation(null)} footer={null}>
        <form className="form-grid" onSubmit={handleUpdate}>
          <div className="form-field">
            <label>Название</label>
            <input className="input" name="name" defaultValue={editLocation?.name} required />
          </div>
          <div className="form-field">
            <label>X</label>
            <input className="number-input" name="x" type="number" defaultValue={editLocation?.x} required />
          </div>
          <div className="form-field">
            <label>Y</label>
            <input className="number-input" name="y" type="number" defaultValue={editLocation?.y} required />
          </div>
          <div className="form-field">
            <label>Z</label>
            <input className="number-input" name="z" type="number" defaultValue={editLocation?.z} required />
          </div>
          <div className="modal-footer">
            <button type="button" className="secondary-btn" onClick={() => setEditLocation(null)}>Отмена</button>
            <button type="submit" className="primary-btn">Сохранить</button>
          </div>
        </form>
      </Modal>

      <Modal
        open={!!deleteContext}
        title="Переназначение локации"
        onClose={() => {
          setDeleteContext(null);
          setReplacementId('');
        }}
        footer={null}
      >
        {deleteContext && (
          <form className="form-grid" onSubmit={(event) => { event.preventDefault(); confirmDeleteWithReplacement(); }}>
            <p>Локация используется у {deleteContext.personIds.length} администраторов. Выберите новую локацию.</p>
            <div className="form-field">
              <label>Новая локация</label>
              <select
                className="select"
                value={replacementId}
                onChange={(event) => setReplacementId(event.target.value ? Number(event.target.value) : '')}
                required
              >
                <option value="">Выберите локацию</option>
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

export default LocationsPage;
