import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { locationsApi, personsApi } from '../apiClient';
import type {
  LocationAddRequest,
  LocationResponse,
  LocationUpdateRequest,
  PersonResponse,
} from '../api/models';
import { loadAllLocations, loadAllPersons } from '../services/entityLoaders';
import { subscribeToEntityChanges } from '../services/events';
import Modal from '../components/Modal';
import { useToast } from '../components/ToastProvider';

const PAGE_SIZE = 10;

type LocationFormErrors = {
  name?: string;
  x?: string;
  y?: string;
  z?: string;
  replacement?: string;
};

type LocationSortField = 'id' | 'name' | 'x' | 'y' | 'z';

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
  const [confirmDelete, setConfirmDelete] = useState<LocationResponse | null>(null);
  const [createErrors, setCreateErrors] = useState<LocationFormErrors>({});
  const [editErrors, setEditErrors] = useState<LocationFormErrors>({});
  const [replacementError, setReplacementError] = useState<string | null>(null);
  const [sortField, setSortField] = useState<LocationSortField | undefined>(undefined);
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');
  const { showToast } = useToast();

  const refreshData = useCallback(async () => {
    try {
      setLoading(true);
      const [locs, people] = await Promise.all([loadAllLocations(), loadAllPersons()]);
      setLocations(locs);
      setPersons(people);
      setError(null);
    } catch (err: any) {
      const message = err?.message ?? 'Не удалось загрузить локации';
      setError(message);
      showToast(message, 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    refreshData();
  }, [refreshData]);

  useEffect(() => {
    const unsubscribe = subscribeToEntityChanges((change) => {
      if (change.entity === 'LOCATION' || change.entity === 'PERSON') {
        refreshData();
      }
    });
    return unsubscribe;
  }, [refreshData]);

  const getSortValue = (location: LocationResponse, field: LocationSortField): number | string | null => {
    switch (field) {
      case 'id':
        return location.id ?? null;
      case 'name':
        return location.name ?? '';
      case 'x':
        return location.x ?? null;
      case 'y':
        return location.y ?? null;
      case 'z':
        return location.z ?? null;
      default:
        return null;
    }
  };

  const sortedLocations = useMemo(() => {
    if (!sortField) {
      return locations;
    }
    const copy = [...locations];
    copy.sort((a, b) => {
      const aValue = getSortValue(a, sortField);
      const bValue = getSortValue(b, sortField);
      let result = 0;
      if (aValue == null && bValue == null) {
        result = 0;
      } else if (aValue == null) {
        result = 1;
      } else if (bValue == null) {
        result = -1;
      } else if (typeof aValue === 'number' && typeof bValue === 'number') {
        result = aValue - bValue;
      } else {
        result = String(aValue).localeCompare(String(bValue), 'ru', { sensitivity: 'accent', numeric: true });
      }
      return sortOrder === 'asc' ? result : -result;
    });
    return copy;
  }, [locations, sortField, sortOrder]);

  const maxPage = Math.max(1, Math.ceil(sortedLocations.length / PAGE_SIZE));
  const currentPage = Math.min(page, maxPage);
  const paginated = sortedLocations.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  const toggleSort = (field: LocationSortField) => {
    if (sortField !== field) {
      setSortField(field);
      setSortOrder('asc');
      setPage(1);
      return;
    }
    if (sortOrder === 'asc') {
      setSortOrder('desc');
      setPage(1);
      return;
    }
    setSortField(undefined);
    setSortOrder('asc');
    setPage(1);
  };

  const sortIndicator = (field: LocationSortField) => {
    if (sortField !== field) return '';
    return sortOrder === 'asc' ? '▲' : '▼';
  };

  const validateLocationForm = (
    formData: FormData,
    setErrors: React.Dispatch<React.SetStateAction<LocationFormErrors>>
  ): { name: string; x: number; y: number; z: number } | null => {
    const errors: LocationFormErrors = {};
    const name = String(formData.get('name') ?? '').trim();
    const rawX = formData.get('x');
    const rawY = formData.get('y');
    const rawZ = formData.get('z');
    const parsedX = rawX !== null && String(rawX).trim() !== '' ? Number(rawX) : NaN;
    const parsedY = rawY !== null && String(rawY).trim() !== '' ? Number(rawY) : NaN;
    const parsedZ = rawZ !== null && String(rawZ).trim() !== '' ? Number(rawZ) : NaN;

    if (!name) {
      errors.name = 'Введите название локации';
    }
    if (rawX === null || String(rawX).trim() === '') {
      errors.x = 'Введите значение X';
    } else if (Number.isNaN(parsedX)) {
      errors.x = 'Значение X должно быть числом';
    }
    if (rawY === null || String(rawY).trim() === '') {
      errors.y = 'Введите значение Y';
    } else if (Number.isNaN(parsedY)) {
      errors.y = 'Значение Y должно быть числом';
    }
    if (rawZ === null || String(rawZ).trim() === '') {
      errors.z = 'Введите значение Z';
    } else if (Number.isNaN(parsedZ)) {
      errors.z = 'Значение Z должно быть числом';
    }

    setErrors(errors);
    if (Object.keys(errors).length > 0) {
      showToast('Исправьте ошибки в форме', 'warning');
      return null;
    }
    return { name, x: parsedX, y: parsedY, z: parsedZ };
  };

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const valid = validateLocationForm(formData, setCreateErrors);
    if (!valid) {
      return;
    }
    const payload: LocationAddRequest = {
      name: valid.name,
      x: valid.x,
      y: valid.y,
      z: valid.z,
    };
    try {
      await locationsApi.apiV1LocationsPost({ locationAddRequest: payload });
      setCreateOpen(false);
      setCreateErrors({});
      await refreshData();
      showToast('Локация создана', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось создать локацию', 'error');
    }
  };

  const handleUpdate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editLocation?.id) return;
    const formData = new FormData(event.currentTarget);
    const valid = validateLocationForm(formData, setEditErrors);
    if (!valid) {
      return;
    }
    const payload: LocationUpdateRequest = {
      name: valid.name,
      x: valid.x,
      y: valid.y,
      z: valid.z,
    };
    try {
      await locationsApi.apiV1LocationsIdPatch({ id: editLocation.id, locationUpdateRequest: payload });
      setEditLocation(null);
      setEditErrors({});
      await refreshData();
      showToast('Локация обновлена', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось обновить локацию', 'error');
    }
  };

  const handleDelete = (location: LocationResponse) => {
    const affected = persons.filter((person) => person.location?.id === location.id).map((p) => p.id!).filter(Boolean);
    if (affected.length === 0) {
      setConfirmDelete(location);
      return;
    }
    setDeleteContext({ location, personIds: affected });
    setReplacementId('');
    setReplacementError(null);
  };

  const confirmDeleteWithReplacement = async () => {
    if (!deleteContext?.location.id || replacementId === '') {
      const message = 'Выберите новую локацию для переназначения';
      setReplacementError(message);
      showToast(message, 'warning');
      return;
    }
    setReplacementError(null);
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
      setReplacementError(null);
      await refreshData();
      showToast('Локация удалена и назначена замена', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось переназначить локации', 'error');
    }
  };

  const confirmDeleteLocation = async () => {
    if (!confirmDelete?.id) {
      setConfirmDelete(null);
      return;
    }
    try {
      await locationsApi.apiV1LocationsIdDelete({ id: confirmDelete.id });
      await refreshData();
      showToast('Локация удалена', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось удалить локацию', 'error');
    } finally {
      setConfirmDelete(null);
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
        <button
          className="primary-btn"
          onClick={() => {
            setCreateErrors({});
            setCreateOpen(true);
          }}
        >
          Добавить
        </button>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              <th onClick={() => toggleSort('id')} style={{ cursor: 'pointer' }}>ID {sortIndicator('id')}</th>
              <th onClick={() => toggleSort('name')} style={{ cursor: 'pointer' }}>Название {sortIndicator('name')}</th>
              <th onClick={() => toggleSort('x')} style={{ cursor: 'pointer' }}>X {sortIndicator('x')}</th>
              <th onClick={() => toggleSort('y')} style={{ cursor: 'pointer' }}>Y {sortIndicator('y')}</th>
              <th onClick={() => toggleSort('z')} style={{ cursor: 'pointer' }}>Z {sortIndicator('z')}</th>
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

      <Modal
        open={createOpen}
        title="Создание локации"
        onClose={() => {
          setCreateOpen(false);
          setCreateErrors({});
        }}
        footer={null}
      >
        <form className="form-grid" onSubmit={handleCreate} noValidate>
          <div className="form-field">
            <label>Название</label>
            <input
              className="input"
              name="name"
              onChange={() => setCreateErrors((prev) => ({ ...prev, name: undefined }))}
            />
            {createErrors.name && <div className="field-error">{createErrors.name}</div>}
          </div>
          <div className="form-field">
            <label>X</label>
            <input
              className="number-input"
              name="x"
              type="number"
              onChange={() => setCreateErrors((prev) => ({ ...prev, x: undefined }))}
            />
            {createErrors.x && <div className="field-error">{createErrors.x}</div>}
          </div>
          <div className="form-field">
            <label>Y</label>
            <input
              className="number-input"
              name="y"
              type="number"
              onChange={() => setCreateErrors((prev) => ({ ...prev, y: undefined }))}
            />
            {createErrors.y && <div className="field-error">{createErrors.y}</div>}
          </div>
          <div className="form-field">
            <label>Z</label>
            <input
              className="number-input"
              name="z"
              type="number"
              onChange={() => setCreateErrors((prev) => ({ ...prev, z: undefined }))}
            />
            {createErrors.z && <div className="field-error">{createErrors.z}</div>}
          </div>
          <div className="modal-footer">
            <button
              type="button"
              className="secondary-btn"
              onClick={() => {
                setCreateOpen(false);
                setCreateErrors({});
              }}
            >
              Отмена
            </button>
            <button type="submit" className="primary-btn">Сохранить</button>
          </div>
        </form>
      </Modal>

      <Modal
        open={!!editLocation}
        title="Редактирование локации"
        onClose={() => {
          setEditLocation(null);
          setEditErrors({});
        }}
        footer={null}
      >
        <form className="form-grid" onSubmit={handleUpdate} noValidate>
          <div className="form-field">
            <label>Название</label>
            <input
              className="input"
              name="name"
              defaultValue={editLocation?.name}
              onChange={() => setEditErrors((prev) => ({ ...prev, name: undefined }))}
            />
            {editErrors.name && <div className="field-error">{editErrors.name}</div>}
          </div>
          <div className="form-field">
            <label>X</label>
            <input
              className="number-input"
              name="x"
              type="number"
              defaultValue={editLocation?.x}
              onChange={() => setEditErrors((prev) => ({ ...prev, x: undefined }))}
            />
            {editErrors.x && <div className="field-error">{editErrors.x}</div>}
          </div>
          <div className="form-field">
            <label>Y</label>
            <input
              className="number-input"
              name="y"
              type="number"
              defaultValue={editLocation?.y}
              onChange={() => setEditErrors((prev) => ({ ...prev, y: undefined }))}
            />
            {editErrors.y && <div className="field-error">{editErrors.y}</div>}
          </div>
          <div className="form-field">
            <label>Z</label>
            <input
              className="number-input"
              name="z"
              type="number"
              defaultValue={editLocation?.z}
              onChange={() => setEditErrors((prev) => ({ ...prev, z: undefined }))}
            />
            {editErrors.z && <div className="field-error">{editErrors.z}</div>}
          </div>
          <div className="modal-footer">
            <button
              type="button"
              className="secondary-btn"
              onClick={() => {
                setEditLocation(null);
                setEditErrors({});
              }}
            >
              Отмена
            </button>
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
          setReplacementError(null);
        }}
        footer={null}
      >
        {deleteContext && (
          <form className="form-grid single-column" onSubmit={(event) => { event.preventDefault(); confirmDeleteWithReplacement(); }}>
            <p>Локация используется у {deleteContext.personIds.length} кураторов. Выберите новую локацию.</p>
            <div className="form-field">
              <label>Новая локация</label>
              <select
                className={`select${replacementError ? ' invalid-field' : ''}`}
                value={replacementId}
                onChange={(event) => {
                  setReplacementId(event.target.value ? Number(event.target.value) : '');
                  setReplacementError(null);
                }}
              >
                <option value="">Выберите локацию</option>
                {replacementOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {replacementError && <div className="field-error">{replacementError}</div>}
            </div>
            <div className="modal-footer">
              <button
                type="button"
                className="secondary-btn"
                onClick={() => {
                  setDeleteContext(null);
                  setReplacementId('');
                  setReplacementError(null);
                }}
              >
                Отмена
              </button>
              <button type="submit" className="danger-btn">Переназначить и удалить</button>
            </div>
          </form>
        )}
      </Modal>

      <Modal
        open={!!confirmDelete}
        title="Удаление локации"
        onClose={() => setConfirmDelete(null)}
        footer={null}
      >
        {confirmDelete && (
          <form className="form-grid" onSubmit={(event) => { event.preventDefault(); confirmDeleteLocation(); }}>
            <p>Удалить локацию <strong>#{confirmDelete.id}</strong> {confirmDelete.name}?</p>
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

export default LocationsPage;
