import { FormEvent, useEffect, useMemo, useState } from 'react';
import Modal from '../../components/Modal';
import type {
  Color,
  CoordinatesResponse,
  Country,
  FormOfEducation,
  LocationResponse,
  PersonResponse,
  Semester,
  StudyGroupAddRequest,
  StudyGroupResponse,
  StudyGroupUpdateRequest,
} from '../../api/models';

const colorValues: Color[] = ['BLACK', 'YELLOW', 'ORANGE'];
const countryValues: Country[] = ['UNITED_KINGDOM', 'FRANCE', 'INDIA', 'VATICAN'];
const educationValues: FormOfEducation[] = ['DISTANCE_EDUCATION', 'FULL_TIME_EDUCATION', 'EVENING_CLASSES'];
const semesterValues: Semester[] = ['FIRST', 'SECOND', 'FOURTH', 'SIXTH', 'SEVENTH'];

type CoordinatesMode = 'existing' | 'new';
type AdminMode = 'none' | 'existing' | 'new';
type LocationMode = 'none' | 'existing' | 'new';

type GroupFormState = {
  name: string;
  studentsCount?: number;
  expelledStudents: number;
  transferredStudents: number;
  formOfEducation?: FormOfEducation;
  shouldBeExpelled: number;
  averageMark?: number;
  semesterEnum: Semester;
  coordinatesMode: CoordinatesMode;
  coordinatesId?: number;
  coordinates?: { x: number; y: number };
  adminMode: AdminMode;
  groupAdminId?: number;
  admin?: {
    name: string;
    eyeColor?: Color;
    hairColor: Color;
    height: number;
    weight: number;
    nationality?: Country;
    locationMode: LocationMode;
    locationId?: number;
    location?: { name: string; x: number; y: number; z: number };
  };
  clearStudentsCount?: boolean;
  clearAverageMark?: boolean;
  clearFormOfEducation?: boolean;
};

interface StudyGroupFormModalProps {
  open: boolean;
  mode: 'create' | 'edit';
  initialValues?: StudyGroupResponse;
  coordinates: CoordinatesResponse[];
  persons: PersonResponse[];
  locations: LocationResponse[];
  onCancel: () => void;
  onSubmit: (payload: StudyGroupAddRequest | { id: number; payload: StudyGroupUpdateRequest }) => Promise<void>;
}

function buildInitialState(initial?: StudyGroupResponse): GroupFormState {
  if (!initial) {
    return {
      name: '',
      expelledStudents: 1,
      transferredStudents: 1,
      shouldBeExpelled: 1,
      semesterEnum: 'FIRST',
      coordinatesMode: 'existing',
      adminMode: 'none',
      admin: {
        name: '',
        hairColor: 'BLACK',
        height: 1,
        weight: 1,
        locationMode: 'none',
      },
    };
  }
  return {
    name: initial.name,
    studentsCount: initial.studentsCount ?? undefined,
    expelledStudents: initial.expelledStudents,
    transferredStudents: initial.transferredStudents,
    formOfEducation: initial.formOfEducation ?? undefined,
    shouldBeExpelled: initial.shouldBeExpelled,
    averageMark: initial.averageMark ?? undefined,
    semesterEnum: initial.semesterEnum,
    coordinatesMode: initial.coordinates?.id ? 'existing' : 'new',
    coordinatesId: initial.coordinates?.id,
    coordinates: initial.coordinates && !initial.coordinates.id ? { x: initial.coordinates.x, y: initial.coordinates.y } : undefined,
    adminMode: initial.groupAdmin?.id ? 'existing' : initial.groupAdmin ? 'new' : 'none',
    groupAdminId: initial.groupAdmin?.id ?? undefined,
    admin: initial.groupAdmin
      ? {
          name: initial.groupAdmin.name,
          eyeColor: initial.groupAdmin.eyeColor ?? undefined,
          hairColor: initial.groupAdmin.hairColor,
          height: initial.groupAdmin.height,
          weight: initial.groupAdmin.weight,
          nationality: initial.groupAdmin.nationality ?? undefined,
          locationMode: initial.groupAdmin.location?.id ? 'existing' : initial.groupAdmin.location ? 'new' : 'none',
          locationId: initial.groupAdmin.location?.id ?? undefined,
          location: initial.groupAdmin.location
            ? {
                name: initial.groupAdmin.location.name,
                x: initial.groupAdmin.location.x,
                y: initial.groupAdmin.location.y,
                z: initial.groupAdmin.location.z,
              }
            : undefined,
        }
      : {
          name: '',
          hairColor: 'BLACK',
          height: 1,
          weight: 1,
          locationMode: 'none',
        },
  };
}

function buildAddPayload(state: GroupFormState): StudyGroupAddRequest {
  return {
    name: state.name,
    studentsCount: state.studentsCount,
    expelledStudents: state.expelledStudents,
    transferredStudents: state.transferredStudents,
    formOfEducation: state.formOfEducation,
    shouldBeExpelled: state.shouldBeExpelled,
    averageMark: state.averageMark,
    semesterEnum: state.semesterEnum,
    coordinatesId: state.coordinatesMode === 'existing' ? state.coordinatesId : undefined,
    coordinates:
      state.coordinatesMode === 'new' && state.coordinates
        ? {
            x: state.coordinates.x,
            y: state.coordinates.y,
          }
        : undefined,
    groupAdminId: state.adminMode === 'existing' ? state.groupAdminId : undefined,
    groupAdmin:
      state.adminMode === 'new' && state.admin
        ? {
            name: state.admin.name,
            eyeColor: state.admin.eyeColor,
            hairColor: state.admin.hairColor,
            height: state.admin.height,
            weight: state.admin.weight,
            nationality: state.admin.nationality,
            locationId: state.admin.locationMode === 'existing' ? state.admin.locationId : undefined,
            location:
              state.admin.locationMode === 'new' && state.admin.location
                ? {
                    name: state.admin.location.name,
                    x: state.admin.location.x,
                    y: state.admin.location.y,
                    z: state.admin.location.z,
                  }
                : undefined,
          }
        : undefined,
  };
}

function buildUpdatePayload(state: GroupFormState): StudyGroupUpdateRequest {
  return {
    name: state.name,
    studentsCount: state.studentsCount,
    clearStudentsCount: state.clearStudentsCount,
    expelledStudents: state.expelledStudents,
    transferredStudents: state.transferredStudents,
    formOfEducation: state.formOfEducation,
    clearFormOfEducation: state.clearFormOfEducation,
    shouldBeExpelled: state.shouldBeExpelled,
    averageMark: state.averageMark,
    clearAverageMark: state.clearAverageMark,
    semesterEnum: state.semesterEnum,
    coordinatesId: state.coordinatesMode === 'existing' ? state.coordinatesId : undefined,
    coordinates:
      state.coordinatesMode === 'new' && state.coordinates
        ? {
            x: state.coordinates.x,
            y: state.coordinates.y,
          }
        : undefined,
    groupAdminId: state.adminMode === 'existing' ? state.groupAdminId : undefined,
    groupAdmin:
      state.adminMode === 'new' && state.admin
        ? {
            name: state.admin.name,
            eyeColor: state.admin.eyeColor,
            hairColor: state.admin.hairColor,
            height: state.admin.height,
            weight: state.admin.weight,
            nationality: state.admin.nationality,
            locationId: state.admin.locationMode === 'existing' ? state.admin.locationId : undefined,
            location:
              state.admin.locationMode === 'new' && state.admin.location
                ? {
                    name: state.admin.location.name,
                    x: state.admin.location.x,
                    y: state.admin.location.y,
                    z: state.admin.location.z,
                  }
                : undefined,
          }
        : undefined,
    removeGroupAdmin: state.adminMode === 'none',
  };
}

const StudyGroupFormModal = ({
  open,
  mode,
  initialValues,
  coordinates,
  persons,
  locations,
  onCancel,
  onSubmit,
}: StudyGroupFormModalProps) => {
  const [state, setState] = useState<GroupFormState>(buildInitialState(initialValues));
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setState(buildInitialState(initialValues));
    }
  }, [open, initialValues]);

  const coordinatesOptions = useMemo(
    () => coordinates.map((coord) => ({ value: coord.id, label: `#${coord.id} (${coord.x}; ${coord.y})` })),
    [coordinates]
  );

  const personsOptions = useMemo(
    () => persons.map((person) => ({ value: person.id, label: `#${person.id} ${person.name}` })),
    [persons]
  );

  const locationsOptions = useMemo(
    () => locations.map((loc) => ({ value: loc.id, label: `#${loc.id} ${loc.name}` })),
    [locations]
  );

  const onChange = <K extends keyof GroupFormState>(key: K, value: GroupFormState[K]) => {
    setState((prev) => ({ ...prev, [key]: value }));
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    try {
      if (mode === 'create') {
        const payload = buildAddPayload(state);
        await onSubmit(payload);
      } else if (initialValues?.id != null) {
        const payload = buildUpdatePayload(state);
        await onSubmit({ id: initialValues.id, payload });
      }
      onCancel();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={mode === 'create' ? 'Создание учебной группы' : 'Редактирование учебной группы'}
      onClose={() => {
        if (!submitting) onCancel();
      }}
      footer={null}
    >
      <form onSubmit={handleSubmit} className="form-grid">
        <div className="form-field full-width">
          <label htmlFor="group-name">Название</label>
          <input
            id="group-name"
            className="input"
            value={state.name}
            onChange={(event) => onChange('name', event.target.value)}
            required
          />
        </div>

        <div className="form-field">
          <label>Количество студентов</label>
          <input
            className="number-input"
            type="number"
            min={1}
            value={state.studentsCount ?? ''}
            onChange={(event) =>
              onChange('studentsCount', event.target.value ? Number(event.target.value) : undefined)
            }
          />
          {mode === 'edit' && (
            <label className="form-inline">
              <input
                type="checkbox"
                checked={state.clearStudentsCount ?? false}
                onChange={(event) => onChange('clearStudentsCount', event.target.checked)}
              />
              очистить значение
            </label>
          )}
        </div>

        <div className="form-field">
          <label>Отчисленных студентов</label>
          <input
            className="number-input"
            type="number"
            min={1}
            value={state.expelledStudents}
            onChange={(event) => onChange('expelledStudents', Number(event.target.value))}
            required
          />
        </div>

        <div className="form-field">
          <label>Переведённых студентов</label>
          <input
            className="number-input"
            type="number"
            min={1}
            value={state.transferredStudents}
            onChange={(event) => onChange('transferredStudents', Number(event.target.value))}
            required
          />
        </div>

        <div className="form-field">
          <label>Форма обучения</label>
          <select
            className="select"
            value={state.formOfEducation ?? ''}
            onChange={(event) =>
              onChange('formOfEducation', (event.target.value || undefined) as FormOfEducation | undefined)
            }
          >
            <option value="">—</option>
            {educationValues.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
          {mode === 'edit' && (
            <label className="form-inline">
              <input
                type="checkbox"
                checked={state.clearFormOfEducation ?? false}
                onChange={(event) => onChange('clearFormOfEducation', event.target.checked)}
              />
              очистить
            </label>
          )}
        </div>

        <div className="form-field">
          <label>Должны быть отчислены</label>
          <input
            className="number-input"
            type="number"
            min={1}
            value={state.shouldBeExpelled}
            onChange={(event) => onChange('shouldBeExpelled', Number(event.target.value))}
            required
          />
        </div>

        <div className="form-field">
          <label>Средний балл</label>
          <input
            className="number-input"
            type="number"
            min={1}
            value={state.averageMark ?? ''}
            onChange={(event) =>
              onChange('averageMark', event.target.value ? Number(event.target.value) : undefined)
            }
          />
          {mode === 'edit' && (
            <label className="form-inline">
              <input
                type="checkbox"
                checked={state.clearAverageMark ?? false}
                onChange={(event) => onChange('clearAverageMark', event.target.checked)}
              />
              очистить
            </label>
          )}
        </div>

        <div className="form-field">
          <label>Семестр</label>
          <select
            className="select"
            value={state.semesterEnum}
            onChange={(event) => onChange('semesterEnum', event.target.value as Semester)}
          >
            {semesterValues.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </div>

        <div className="form-field">
          <label>Координаты</label>
          <select
            className="select"
            value={state.coordinatesMode}
            onChange={(event) => onChange('coordinatesMode', event.target.value as CoordinatesMode)}
          >
            <option value="existing">Существующие</option>
            <option value="new">Новые</option>
          </select>
          {state.coordinatesMode === 'existing' ? (
            <select
              className="select"
              value={state.coordinatesId ?? ''}
              onChange={(event) =>
                onChange('coordinatesId', event.target.value ? Number(event.target.value) : undefined)
              }
              required
            >
              <option value="">Выберите координаты</option>
              {coordinatesOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          ) : (
            <div className="form-inline" style={{ gap: '8px' }}>
              <input
                className="number-input"
                type="number"
                placeholder="X"
                value={state.coordinates?.x ?? ''}
                onChange={(event) =>
                  setState((prev) => ({
                    ...prev,
                    coordinates: {
                      x: Number(event.target.value),
                      y: prev.coordinates?.y ?? 0,
                    },
                  }))
                }
                required
              />
              <input
                className="number-input"
                type="number"
                placeholder="Y"
                value={state.coordinates?.y ?? ''}
                onChange={(event) =>
                  setState((prev) => ({
                    ...prev,
                    coordinates: {
                      x: prev.coordinates?.x ?? 0,
                      y: Number(event.target.value),
                    },
                  }))
                }
                required
              />
            </div>
          )}
        </div>

        <div className="form-field">
          <label>Куратор</label>
          <select
            className="select"
            value={state.adminMode}
            onChange={(event) => onChange('adminMode', event.target.value as AdminMode)}
          >
            <option value="none">Без куратора</option>
            <option value="existing">Существующий</option>
            <option value="new">Новый</option>
          </select>
          {state.adminMode === 'existing' ? (
            <select
              className="select"
              value={state.groupAdminId ?? ''}
              onChange={(event) =>
                onChange('groupAdminId', event.target.value ? Number(event.target.value) : undefined)
              }
              required
            >
              <option value="">Выберите куратора</option>
              {personsOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          ) : null}
        </div>

        {state.adminMode === 'new' && state.admin && (
          <div className="form-field full-width">
            <div className="form-grid single-column">
              <div className="form-field">
                <label>Имя куратора</label>
                <input
                  className="input"
                  value={state.admin.name}
                  onChange={(event) =>
                    setState((prev) => ({
                      ...prev,
                      admin: {
                        ...prev.admin!,
                        name: event.target.value,
                      },
                    }))
                  }
                  required
                />
              </div>
              <div className="form-field">
                <label>Цвет глаз</label>
                <select
                  className="select"
                  value={state.admin.eyeColor ?? ''}
                  onChange={(event) =>
                    setState((prev) => ({
                      ...prev,
                      admin: {
                        ...prev.admin!,
                        eyeColor: (event.target.value || undefined) as Color | undefined,
                      },
                    }))
                  }
                >
                  <option value="">—</option>
                  {colorValues.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-field">
                <label>Цвет волос</label>
                <select
                  className="select"
                  value={state.admin.hairColor}
                  onChange={(event) =>
                    setState((prev) => ({
                      ...prev,
                      admin: {
                        ...prev.admin!,
                        hairColor: event.target.value as Color,
                      },
                    }))
                  }
                  required
                >
                  {colorValues.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-field">
                <label>Рост</label>
                <input
                  className="number-input"
                  type="number"
                  min={1}
                  value={state.admin.height}
                  onChange={(event) =>
                    setState((prev) => ({
                      ...prev,
                      admin: {
                        ...prev.admin!,
                        height: Number(event.target.value),
                      },
                    }))
                  }
                  required
                />
              </div>
              <div className="form-field">
                <label>Вес</label>
                <input
                  className="number-input"
                  type="number"
                  min={1}
                  step={0.1}
                  value={state.admin.weight}
                  onChange={(event) =>
                    setState((prev) => ({
                      ...prev,
                      admin: {
                        ...prev.admin!,
                        weight: Number(event.target.value),
                      },
                    }))
                  }
                  required
                />
              </div>
              <div className="form-field">
                <label>Национальность</label>
                <select
                  className="select"
                  value={state.admin.nationality ?? ''}
                  onChange={(event) =>
                    setState((prev) => ({
                      ...prev,
                      admin: {
                        ...prev.admin!,
                        nationality: (event.target.value || undefined) as Country | undefined,
                      },
                    }))
                  }
                >
                  <option value="">—</option>
                  {countryValues.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-field">
                <label>Локация куратора</label>
                <select
                  className="select"
                  value={state.admin.locationMode}
                  onChange={(event) =>
                    setState((prev) => ({
                      ...prev,
                      admin: {
                        ...prev.admin!,
                        locationMode: event.target.value as LocationMode,
                      },
                    }))
                  }
                >
                  <option value="none">Без локации</option>
                  <option value="existing">Существующая</option>
                  <option value="new">Новая</option>
                </select>
                {state.admin.locationMode === 'existing' ? (
                  <select
                    className="select"
                    value={state.admin.locationId ?? ''}
                    onChange={(event) =>
                      setState((prev) => ({
                        ...prev,
                        admin: {
                          ...prev.admin!,
                          locationId: event.target.value ? Number(event.target.value) : undefined,
                        },
                      }))
                    }
                    required
                  >
                    <option value="">Выберите локацию</option>
                    {locationsOptions.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                ) : null}
                {state.admin.locationMode === 'new' ? (
                  <div className="form-inline" style={{ gap: '8px' }}>
                    <input
                      className="input"
                      placeholder="Название"
                      value={state.admin.location?.name ?? ''}
                      onChange={(event) =>
                        setState((prev) => ({
                          ...prev,
                          admin: {
                            ...prev.admin!,
                            location: {
                              name: event.target.value,
                              x: prev.admin?.location?.x ?? 0,
                              y: prev.admin?.location?.y ?? 0,
                              z: prev.admin?.location?.z ?? 0,
                            },
                          },
                        }))
                      }
                      required
                    />
                    <input
                      className="number-input"
                      type="number"
                      placeholder="X"
                      value={state.admin.location?.x ?? ''}
                      onChange={(event) =>
                        setState((prev) => ({
                          ...prev,
                          admin: {
                            ...prev.admin!,
                            location: {
                              name: prev.admin?.location?.name ?? '',
                              x: Number(event.target.value),
                              y: prev.admin?.location?.y ?? 0,
                              z: prev.admin?.location?.z ?? 0,
                            },
                          },
                        }))
                      }
                      required
                    />
                    <input
                      className="number-input"
                      type="number"
                      placeholder="Y"
                      value={state.admin.location?.y ?? ''}
                      onChange={(event) =>
                        setState((prev) => ({
                          ...prev,
                          admin: {
                            ...prev.admin!,
                            location: {
                              name: prev.admin?.location?.name ?? '',
                              x: prev.admin?.location?.x ?? 0,
                              y: Number(event.target.value),
                              z: prev.admin?.location?.z ?? 0,
                            },
                          },
                        }))
                      }
                      required
                    />
                    <input
                      className="number-input"
                      type="number"
                      placeholder="Z"
                      value={state.admin.location?.z ?? ''}
                      onChange={(event) =>
                        setState((prev) => ({
                          ...prev,
                          admin: {
                            ...prev.admin!,
                            location: {
                              name: prev.admin?.location?.name ?? '',
                              x: prev.admin?.location?.x ?? 0,
                              y: prev.admin?.location?.y ?? 0,
                              z: Number(event.target.value),
                            },
                          },
                        }))
                      }
                      required
                    />
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        )}

        <div className="modal-footer full-width">
          <button type="button" className="secondary-btn" onClick={onCancel} disabled={submitting}>
            Отмена
          </button>
          <button type="submit" className="primary-btn" disabled={submitting}>
            {submitting ? 'Сохранение...' : mode === 'create' ? 'Создать' : 'Сохранить'}
          </button>
        </div>
      </form>
    </Modal>
  );
};

export default StudyGroupFormModal;
