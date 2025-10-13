import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { personsApi, studyGroupsApi } from '../apiClient';
import type {
  PersonAddRequest,
  PersonResponse,
  PersonUpdateRequest,
  LocationResponse,
  Color,
  Country,
  StudyGroupResponse,
} from '../api/models';
import { loadAllLocations, loadAllPersons, loadAllStudyGroups } from '../services/entityLoaders';
import { subscribeToEntityChanges } from '../services/events';
import Modal from '../components/Modal';
import { useToast } from '../components/ToastProvider';

const PAGE_SIZE = 10;
const colorValues: Color[] = ['BLACK', 'YELLOW', 'ORANGE'];
const countryValues: Country[] = ['UNITED_KINGDOM', 'FRANCE', 'INDIA', 'VATICAN'];
type LocationMode = 'none' | 'existing' | 'new';

type PersonFormErrors = {
  name?: string;
  hairColor?: string;
  height?: string;
  weight?: string;
  location?: string;
};

const resolveLocationMode = (person?: PersonResponse | null): LocationMode => {
  if (!person?.location) {
    return 'none';
  }
  return person.location.id ? 'existing' : 'new';
};

const PersonsPage = () => {
  const { showToast } = useToast();
  const [persons, setPersons] = useState<PersonResponse[]>([]);
  const [locations, setLocations] = useState<LocationResponse[]>([]);
  const [studyGroups, setStudyGroups] = useState<StudyGroupResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [createOpen, setCreateOpen] = useState(false);
  const [editPerson, setEditPerson] = useState<PersonResponse | null>(null);
  const [deleteContext, setDeleteContext] = useState<{ person: PersonResponse; groupIds: number[] } | null>(null);
  const [replacementId, setReplacementId] = useState<number | ''>('');
  const [confirmDelete, setConfirmDelete] = useState<PersonResponse | null>(null);
  const [createLocationMode, setCreateLocationMode] = useState<LocationMode>('none');
  const [editLocationMode, setEditLocationMode] = useState<LocationMode>('none');
  const [createErrors, setCreateErrors] = useState<PersonFormErrors>({});
  const [editErrors, setEditErrors] = useState<PersonFormErrors>({});

  const refreshData = useCallback(async () => {
    try {
      setLoading(true);
      const [people, locs, groups] = await Promise.all([
        loadAllPersons(),
        loadAllLocations(),
        loadAllStudyGroups(),
      ]);
      setPersons(people);
      setLocations(locs);
      setStudyGroups(groups);
      setError(null);
    } catch (err: any) {
      const message = err?.message ?? 'Не удалось загрузить данные';
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
      if (change.entity === 'PERSON' || change.entity === 'STUDY_GROUP' || change.entity === 'LOCATION') {
        refreshData();
      }
    });
    return unsubscribe;
  }, [refreshData]);

  const maxPage = Math.max(1, Math.ceil(persons.length / PAGE_SIZE));
  const currentPage = Math.min(page, maxPage);
  const paginated = persons.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  const validatePersonForm = (formData: FormData, setErrors: React.Dispatch<React.SetStateAction<PersonFormErrors>>) => {
    const errors: PersonFormErrors = {};
    const name = String(formData.get('name') ?? '').trim();
    if (!name) {
      errors.name = 'Введите ФИО';
    }
    if (!formData.get('hairColor')) {
      errors.hairColor = 'Выберите цвет волос';
    }
    const height = Number(formData.get('height'));
    if (!formData.get('height') || Number.isNaN(height) || height <= 0) {
      errors.height = 'Укажите рост больше 0';
    }
    const weight = Number(formData.get('weight'));
    if (!formData.get('weight') || Number.isNaN(weight) || weight <= 0) {
      errors.weight = 'Укажите вес больше 0';
    }
    const locationMode = formData.get('locationMode');
    if (locationMode === 'existing' && !formData.get('locationId')) {
      errors.location = 'Выберите локацию для куратора';
    }
    if (
      locationMode === 'new' &&
      (!formData.get('locationName') || !formData.get('locationX') || !formData.get('locationY') || !formData.get('locationZ'))
    ) {
      errors.location = 'Заполните все поля новой локации';
    }
    setErrors(errors);
    if (Object.keys(errors).length > 0) {
      showToast('Исправьте ошибки в форме', 'warning');
      return false;
    }
    return true;
  };

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    if (!validatePersonForm(formData, setCreateErrors)) {
      return;
    }
    const locationMode = formData.get('locationMode');
    const payload: PersonAddRequest = {
      name: String(formData.get('name')),
      eyeColor: (formData.get('eyeColor') || undefined) as Color | undefined,
      hairColor: formData.get('hairColor') as Color,
      height: Number(formData.get('height')),
      weight: Number(formData.get('weight')),
      nationality: (formData.get('nationality') || undefined) as Country | undefined,
      locationId: locationMode === 'existing' ? Number(formData.get('locationId')) : undefined,
      location:
        locationMode === 'new'
          ? {
              name: String(formData.get('locationName')),
              x: Number(formData.get('locationX')),
              y: Number(formData.get('locationY')),
              z: Number(formData.get('locationZ')),
            }
          : undefined,
    };
    try {
      await personsApi.apiV1PersonsPost({ personAddRequest: payload });
      setCreateOpen(false);
      setCreateLocationMode('none');
      setCreateErrors({});
      await refreshData();
      showToast('Куратор создан', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось создать куратора', 'error');
    }
  };

  const handleUpdate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editPerson?.id) return;
    const formData = new FormData(event.currentTarget);
    if (!validatePersonForm(formData, setEditErrors)) {
      return;
    }
    const locationMode = formData.get('locationMode');
    const payload: PersonUpdateRequest = {
      name: String(formData.get('name')),
      eyeColor: (formData.get('eyeColor') || undefined) as Color | undefined,
      hairColor: formData.get('hairColor') as Color,
      height: Number(formData.get('height')),
      weight: Number(formData.get('weight')),
      nationality: (formData.get('nationality') || undefined) as Country | undefined,
      locationId: locationMode === 'existing' ? Number(formData.get('locationId')) : undefined,
      location:
        locationMode === 'new'
          ? {
              name: String(formData.get('locationName')),
              x: Number(formData.get('locationX')),
              y: Number(formData.get('locationY')),
              z: Number(formData.get('locationZ')),
            }
          : undefined,
      removeLocation: locationMode === 'none',
    };
    try {
      await personsApi.apiV1PersonsIdPatch({ id: editPerson.id, personUpdateRequest: payload });
      setEditPerson(null);
      setEditLocationMode('none');
      setEditErrors({});
      await refreshData();
      showToast('Данные куратора обновлены', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось обновить куратора', 'error');
    }
  };

  const handleDelete = (person: PersonResponse) => {
    const affected = (studyGroups as any[])
      .filter((group) => group.groupAdmin?.id === person.id)
      .map((g) => g.id)
      .filter(Boolean);
    if (affected.length === 0) {
      setConfirmDelete(person);
      return;
    }
    setDeleteContext({ person, groupIds: affected });
    setReplacementId('');
  };

  const confirmDeleteWithReplacement = async () => {
    if (!deleteContext?.person.id || replacementId === '') {
      showToast('Выберите куратора для переназначения', 'warning');
      return;
    }
    try {
      await Promise.all(
        deleteContext.groupIds.map((id) =>
          studyGroupsApi.apiV1StudyGroupsIdPatch({
            id,
            studyGroupUpdateRequest: { groupAdminId: Number(replacementId) },
          })
        )
      );
      await personsApi.apiV1PersonsIdDelete({ id: deleteContext.person.id });
      setDeleteContext(null);
      setReplacementId('');
      await refreshData();
      showToast('Куратор удалён и замена назначена', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось переназначить куратора', 'error');
    }
  };

  const confirmDeletePerson = async () => {
    if (!confirmDelete?.id) {
      setConfirmDelete(null);
      return;
    }
    try {
      await personsApi.apiV1PersonsIdDelete({ id: confirmDelete.id });
      await refreshData();
      showToast('Куратор удалён', 'success');
    } catch (err: any) {
      showToast(err?.message ?? 'Не удалось удалить куратора', 'error');
    } finally {
      setConfirmDelete(null);
    }
  };

  const replacementOptions = useMemo(
    () =>
      persons
        .filter((person) => (deleteContext ? person.id !== deleteContext.person.id : true))
        .map((person) => ({ label: `#${person.id} ${person.name}`, value: person.id! })),
    [persons, deleteContext]
  );

  const locationOptions = locations.map((location) => ({ label: `#${location.id} ${location.name}`, value: location.id! }));

  const formatLocation = (location?: LocationResponse | null) => {
    if (!location) {
      return '—';
    }
    const coordinates = [location.x, location.y, location.z]
      .filter((value) => value !== undefined && value !== null)
      .join(', ');
    return `#${location.id ?? '?'} ${location.name}${coordinates ? ` (${coordinates})` : ''}`;
  };

  const renderPersonForm = (
    person: PersonResponse | undefined,
    locationMode: LocationMode,
    onLocationModeChange: (mode: LocationMode) => void,
    onSubmit: (event: FormEvent<HTMLFormElement>) => void,
    onCancel: () => void,
    errors: PersonFormErrors,
    setErrors: React.Dispatch<React.SetStateAction<PersonFormErrors>>
  ) => (
    <form className="form-grid" onSubmit={onSubmit}>
      <div className="form-field">
        <label>ФИО</label>
        <input
          className="input"
          name="name"
          defaultValue={person?.name}
          onChange={() => setErrors((prev) => ({ ...prev, name: undefined }))}
        />
        {errors.name && <div className="field-error">{errors.name}</div>}
      </div>
      <div className="form-field">
        <label>Цвет глаз</label>
        <select className="select" name="eyeColor" defaultValue={person?.eyeColor ?? ''}>
          <option value="">—</option>
          {colorValues.map((value) => (
            <option key={value} value={value}>{value}</option>
          ))}
        </select>
      </div>
      <div className="form-field">
        <label>Цвет волос</label>
        <select
          className="select"
          name="hairColor"
          defaultValue={person?.hairColor ?? ''}
          onChange={() => setErrors((prev) => ({ ...prev, hairColor: undefined }))}
        >
          <option value="">—</option>
          {colorValues.map((value) => (
            <option key={value} value={value}>{value}</option>
          ))}
        </select>
        {errors.hairColor && <div className="field-error">{errors.hairColor}</div>}
      </div>
      <div className="form-field">
        <label>Рост</label>
        <input
          className="number-input"
          name="height"
          type="number"
          min={1}
          defaultValue={person?.height ?? 1}
          onChange={() => setErrors((prev) => ({ ...prev, height: undefined }))}
        />
        {errors.height && <div className="field-error">{errors.height}</div>}
      </div>
      <div className="form-field">
        <label>Вес</label>
        <input
          className="number-input"
          name="weight"
          type="number"
          min={1}
          step={0.1}
          defaultValue={person?.weight ?? 1}
          onChange={() => setErrors((prev) => ({ ...prev, weight: undefined }))}
        />
        {errors.weight && <div className="field-error">{errors.weight}</div>}
      </div>
      <div className="form-field">
        <label>Национальность</label>
        <select className="select" name="nationality" defaultValue={person?.nationality ?? ''}>
          <option value="">—</option>
          {countryValues.map((value) => (
            <option key={value} value={value}>{value}</option>
          ))}
        </select>
      </div>
      <div className="form-field">
        <label>Локация</label>
        <select
          className="select"
          name="locationMode"
          value={locationMode}
          onChange={(event) => {
            onLocationModeChange(event.target.value as LocationMode);
            setErrors((prev) => ({ ...prev, location: undefined }));
          }}
        >
          <option value="none">Без локации</option>
          <option value="existing">Существующая</option>
          <option value="new">Новая</option>
        </select>
      </div>
      {locationMode === 'existing' && (
        <div className="form-field full-width">
          <label>Выберите локацию</label>
          <select
            className="select"
            name="locationId"
            defaultValue={person?.location?.id ?? ''}
            onChange={() => setErrors((prev) => ({ ...prev, location: undefined }))}
          >
            <option value="">—</option>
            {locationOptions.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
          {errors.location && <div className="field-error">{errors.location}</div>}
        </div>
      )}
      {locationMode === 'new' && (
        <div className="form-field full-width">
          <label>Новая локация</label>
          <div className="form-row">
            <input
              className="input"
              name="locationName"
              placeholder="Название"
              defaultValue={person?.location?.name ?? ''}
              onChange={() => setErrors((prev) => ({ ...prev, location: undefined }))}
            />
            <input
              className="number-input"
              name="locationX"
              type="number"
              placeholder="X"
              defaultValue={person?.location?.x ?? ''}
              onChange={() => setErrors((prev) => ({ ...prev, location: undefined }))}
            />
            <input
              className="number-input"
              name="locationY"
              type="number"
              placeholder="Y"
              defaultValue={person?.location?.y ?? ''}
              onChange={() => setErrors((prev) => ({ ...prev, location: undefined }))}
            />
            <input
              className="number-input"
              name="locationZ"
              type="number"
              placeholder="Z"
              defaultValue={person?.location?.z ?? ''}
              onChange={() => setErrors((prev) => ({ ...prev, location: undefined }))}
            />
          </div>
          {errors.location && <div className="field-error">{errors.location}</div>}
        </div>
      )}
      <div className="modal-footer">
        <button type="button" className="secondary-btn" onClick={onCancel}>Отмена</button>
        <button type="submit" className="primary-btn">Сохранить</button>
      </div>
    </form>
  );

  return (
    <div>
      <div className="section-heading">
        <h2>Люди</h2>
        <button
          className="primary-btn"
          onClick={() => {
            setCreateLocationMode('none');
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
              <th>ID</th>
              <th>Имя</th>
              <th>Цвет глаз</th>
              <th>Цвет волос</th>
              <th>Рост</th>
              <th>Вес</th>
              <th>Национальность</th>
              <th>Локация</th>
              <th>Действия</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={9}>Загрузка...</td></tr>
            ) : paginated.length === 0 ? (
              <tr><td colSpan={9}>Нет данных</td></tr>
            ) : (
              paginated.map((person) => (
                <tr key={person.id}>
                  <td>{person.id}</td>
                  <td>{person.name}</td>
                  <td>{person.eyeColor ?? '—'}</td>
                  <td>{person.hairColor}</td>
                  <td>{person.height}</td>
                  <td>{person.weight}</td>
                  <td>{person.nationality ?? '—'}</td>
                  <td>{formatLocation(person.location)}</td>
                  <td>
                    <div className="form-inline">
                      <button
                        className="secondary-btn"
                        onClick={() => {
                          setEditPerson(person);
                          setEditLocationMode(resolveLocationMode(person));
                        }}
                      >
                        Изменить
                      </button>
                      <button className="danger-btn" onClick={() => handleDelete(person)}>Удалить</button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
        <button className="secondary-btn" onClick={() => setPage((prev) => Math.max(1, prev - 1))} disabled={currentPage === 1}>Назад</button>
        <span>Страница {currentPage} из {maxPage}</span>
        <button className="secondary-btn" onClick={() => setPage((prev) => Math.min(maxPage, prev + 1))} disabled={currentPage === maxPage}>Вперёд</button>
      </div>

      <Modal
        open={createOpen}
        title="Создание человека"
        onClose={() => {
          setCreateOpen(false);
          setCreateLocationMode('none');
        }}
        footer={null}
      >
        {renderPersonForm(
          undefined,
          createLocationMode,
          (mode) => setCreateLocationMode(mode),
          handleCreate,
          () => {
            setCreateOpen(false);
            setCreateLocationMode('none');
            setCreateErrors({});
          },
          createErrors,
          setCreateErrors
        )}
      </Modal>

      <Modal
        open={!!editPerson}
        title="Редактирование куратора"
        onClose={() => {
          setEditPerson(null);
          setEditLocationMode('none');
        }}
        footer={null}
      >
        {renderPersonForm(
          editPerson ?? undefined,
          editLocationMode,
          (mode) => setEditLocationMode(mode),
          handleUpdate,
          () => {
            setEditPerson(null);
            setEditLocationMode('none');
            setEditErrors({});
          },
          editErrors,
          setEditErrors
        )}
      </Modal>

      <Modal
        open={!!deleteContext}
        title="Переназначение куратора"
        onClose={() => {
          setDeleteContext(null);
          setReplacementId('');
        }}
        footer={null}
      >
        {deleteContext && (
          <form className="form-grid single-column" onSubmit={(event) => { event.preventDefault(); confirmDeleteWithReplacement(); }}>
            <p>Куратор закреплён за {deleteContext.groupIds.length} учебными группами. Выберите замену.</p>
            <div className="form-field">
              <label>Новый куратор</label>
              <select
                className="select"
                value={replacementId}
                onChange={(event) => setReplacementId(event.target.value ? Number(event.target.value) : '')}
                required
              >
                <option value="">Выберите куратора</option>
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
      </Modal>

      <Modal
        open={!!confirmDelete}
        title="Удаление куратора"
        onClose={() => setConfirmDelete(null)}
        footer={null}
      >
        {confirmDelete && (
          <form className="form-grid" onSubmit={(event) => { event.preventDefault(); confirmDeletePerson(); }}>
            <p>Удалить куратора <strong>{confirmDelete.name}</strong>? Связанные учебные группы не затронуты.</p>
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

export default PersonsPage;
