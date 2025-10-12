import { useEffect, useMemo, useState } from 'react';
import {
  StudyGroupResponse,
  StudyGroupAddRequest,
  StudyGroupUpdateRequest,
  CoordinatesResponse,
  PersonResponse,
  LocationResponse,
} from '../api/models';
import { studyGroupsApi } from '../apiClient';
import {
  loadAllCoordinates,
  loadAllLocations,
  loadAllPersons,
  loadAllStudyGroups,
} from '../services/entityLoaders';
import StudyGroupFormModal from '../components/study-groups/StudyGroupFormModal';
import { formatDateTime } from '../utils/strings';

const PAGE_SIZE = 10;

const StudyGroupsPage = () => {
  const [groups, setGroups] = useState<StudyGroupResponse[]>([]);
  const [coordinates, setCoordinates] = useState<CoordinatesResponse[]>([]);
  const [persons, setPersons] = useState<PersonResponse[]>([]);
  const [locations, setLocations] = useState<LocationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState({ name: '', admin: '' });
  const [page, setPage] = useState(1);
  const [selectedGroup, setSelectedGroup] = useState<StudyGroupResponse | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<'create' | 'edit'>('create');
  const [editingGroup, setEditingGroup] = useState<StudyGroupResponse | undefined>(undefined);

  const refreshData = async () => {
    try {
      setLoading(true);
      const [groupsData, coordsData, personsData, locationsData] = await Promise.all([
        loadAllStudyGroups(),
        loadAllCoordinates(),
        loadAllPersons(),
        loadAllLocations(),
      ]);
      setGroups(groupsData);
      setCoordinates(coordsData);
      setPersons(personsData);
      setLocations(locationsData);
      setError(null);
    } catch (err: any) {
      setError(err?.message ?? 'Не удалось загрузить данные');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refreshData();
    const interval = setInterval(refreshData, 5000);
    return () => clearInterval(interval);
  }, []);

  const filteredGroups = useMemo(() => {
    return groups.filter((group) => {
      if (filters.name && !group.name.toLowerCase().includes(filters.name.toLowerCase())) {
        return false;
      }
      if (filters.admin) {
        const adminName = group.groupAdmin?.name ?? '';
        if (adminName.toLowerCase() !== filters.admin.toLowerCase()) {
          return false;
        }
      }
      return true;
    });
  }, [groups, filters]);

  const maxPage = Math.max(1, Math.ceil(filteredGroups.length / PAGE_SIZE));
  const currentPage = Math.min(page, maxPage);
  const paginatedGroups = filteredGroups.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  const openCreateForm = () => {
    setFormMode('create');
    setEditingGroup(undefined);
    setFormOpen(true);
  };

  const openEditForm = (group: StudyGroupResponse) => {
    setFormMode('edit');
    setEditingGroup(group);
    setFormOpen(true);
  };

  const handleDelete = async (group: StudyGroupResponse) => {
    if (!window.confirm(`Удалить учебную группу "${group.name}"?`)) {
      return;
    }
    try {
      await studyGroupsApi.apiV1StudyGroupsIdDelete({ id: group.id! });
      await refreshData();
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось удалить группу');
    }
  };

  const handleSubmit = async (
    payload: StudyGroupAddRequest | { id: number; payload: StudyGroupUpdateRequest }
  ) => {
    if ('semesterEnum' in payload) {
      await studyGroupsApi.apiV1StudyGroupsPost({ studyGroupAddRequest: payload });
    } else {
      await studyGroupsApi.apiV1StudyGroupsIdPatch({
        id: payload.id,
        studyGroupUpdateRequest: payload.payload,
      });
    }
    await refreshData();
  };

  return (
    <div>
      <div className="section-heading">
        <h2>Учебные группы</h2>
        <div className="table-toolbar">
          <button className="primary-btn" onClick={openCreateForm}>Создать группу</button>
          <button className="secondary-btn" onClick={refreshData} disabled={loading}>Обновить</button>
        </div>
      </div>

      <div className="filter-row">
        <input
          className="input"
          placeholder="Фильтр по названию"
          value={filters.name}
          onChange={(event) => setFilters((prev) => ({ ...prev, name: event.target.value }))}
        />
        <input
          className="input"
          placeholder="Фильтр по администратору (точное совпадение)"
          value={filters.admin}
          onChange={(event) => setFilters((prev) => ({ ...prev, admin: event.target.value }))}
        />
      </div>

      {error && <div style={{ color: '#dc2626', marginBottom: 12 }}>{error}</div>}

      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Название</th>
              <th>Семестр</th>
              <th>Студентов</th>
              <th>Отчислено</th>
              <th>Переведено</th>
              <th>Администратор</th>
              <th>Создано</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={9}>Загрузка...</td>
              </tr>
            ) : paginatedGroups.length === 0 ? (
              <tr>
                <td colSpan={9}>Нет данных</td>
              </tr>
            ) : (
              paginatedGroups.map((group) => (
                <tr key={group.id} onClick={() => setSelectedGroup(group)} style={{ cursor: 'pointer' }}>
                  <td>{group.id}</td>
                  <td>{group.name}</td>
                  <td>{group.semesterEnum}</td>
                  <td>{group.studentsCount ?? '—'}</td>
                  <td>{group.expelledStudents}</td>
                  <td>{group.transferredStudents}</td>
                  <td>{group.groupAdmin?.name ?? '—'}</td>
                  <td>{formatDateTime(group.creationDate)}</td>
                  <td>
                    <div className="form-inline">
                      <button
                        type="button"
                        className="secondary-btn"
                        onClick={(event) => {
                          event.stopPropagation();
                          openEditForm(group);
                        }}
                      >
                        Изменить
                      </button>
                      <button
                        type="button"
                        className="danger-btn"
                        onClick={(event) => {
                          event.stopPropagation();
                          handleDelete(group);
                        }}
                      >
                        Удалить
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
        <button
          className="secondary-btn"
          onClick={() => setPage((prev) => Math.max(1, prev - 1))}
          disabled={currentPage === 1}
        >
          Назад
        </button>
        <span>
          Страница {currentPage} из {maxPage}
        </span>
        <button
          className="secondary-btn"
          onClick={() => setPage((prev) => Math.min(maxPage, prev + 1))}
          disabled={currentPage === maxPage}
        >
          Вперёд
        </button>
      </div>

      {selectedGroup && (
        <div className="drawer">
          <div className="drawer-header">
            <h3>{selectedGroup.name}</h3>
            <button className="secondary-btn" onClick={() => setSelectedGroup(null)}>Закрыть</button>
          </div>
          <div className="drawer-field"><strong>Семестр:</strong> {selectedGroup.semesterEnum}</div>
          <div className="drawer-field"><strong>Координаты:</strong> X = {selectedGroup.coordinates?.x}, Y = {selectedGroup.coordinates?.y}</div>
          <div className="drawer-field"><strong>Студентов:</strong> {selectedGroup.studentsCount ?? '—'}</div>
          <div className="drawer-field"><strong>Отчислено:</strong> {selectedGroup.expelledStudents}</div>
          <div className="drawer-field"><strong>Переведено:</strong> {selectedGroup.transferredStudents}</div>
          <div className="drawer-field"><strong>Должны быть отчислены:</strong> {selectedGroup.shouldBeExpelled}</div>
          <div className="drawer-field"><strong>Средний балл:</strong> {selectedGroup.averageMark ?? '—'}</div>
          <div className="drawer-field"><strong>Администратор:</strong> {selectedGroup.groupAdmin?.name ?? '—'}</div>
          <div className="drawer-field"><strong>Создано:</strong> {formatDateTime(selectedGroup.creationDate)}</div>
          <button className="primary-btn" onClick={() => openEditForm(selectedGroup)}>Редактировать</button>
        </div>
      )}

      <StudyGroupFormModal
        open={formOpen}
        mode={formMode}
        initialValues={editingGroup}
        coordinates={coordinates}
        persons={persons}
        locations={locations}
        onCancel={() => setFormOpen(false)}
        onSubmit={handleSubmit}
      />
    </div>
  );
};

export default StudyGroupsPage;
