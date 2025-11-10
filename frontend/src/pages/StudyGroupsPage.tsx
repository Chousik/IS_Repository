import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  CoordinatesResponse,
  LocationResponse,
  PersonResponse,
  StudyGroupAddRequest,
  StudyGroupResponse,
  StudyGroupUpdateRequest,
} from '../api/models';
import { studyGroupsApi } from '../apiClient';
import {
  loadAllCoordinates,
  loadAllLocations,
  loadAllPersons,
} from '../services/entityLoaders';
import StudyGroupFormModal from '../components/study-groups/StudyGroupFormModal';
import Modal from '../components/Modal';
import { formatDateTime } from '../utils/strings';
import { mapPageModel, PagedResult } from '../utils/pagination';
import { subscribeToEntityChanges, EntityChange } from '../services/events';
import { useToast } from '../components/ToastProvider';
import { resolveApiErrorMessage } from '../utils/apiErrors';

const PAGE_SIZE = 10;

type SortOrder = 'asc' | 'desc';

type PagingState = {
  page: number;
  size: number;
  sortField?: string;
  sortOrder: SortOrder;
};

const initialPaged: PagedResult<StudyGroupResponse> = {
  content: [],
  page: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
};

function nextSortState(currentField: string | undefined, currentOrder: SortOrder, targetField: string) {
  if (currentField !== targetField) {
    return { field: targetField, order: 'asc' as SortOrder };
  }
  if (currentOrder === 'asc') {
    return { field: targetField, order: 'desc' as SortOrder };
  }
  return { field: undefined, order: 'asc' as SortOrder };
}

function sortIndicator(field: string | undefined, order: SortOrder, target: string) {
  if (field !== target) {
    return '';
  }
  return order === 'asc' ? '▲' : '▼';
}

const StudyGroupsPage = () => {
  const { showToast } = useToast();
  const [paging, setPaging] = useState<PagingState>({ page: 1, size: PAGE_SIZE, sortField: undefined, sortOrder: 'asc' });
  const [pagedData, setPagedData] = useState<PagedResult<StudyGroupResponse>>(initialPaged);
  const [loadingTable, setLoadingTable] = useState(false);
  const [tableError, setTableError] = useState<string | null>(null);
  const [coordinates, setCoordinates] = useState<CoordinatesResponse[]>([]);
  const [persons, setPersons] = useState<PersonResponse[]>([]);
  const [locations, setLocations] = useState<LocationResponse[]>([]);
  const [selectedGroup, setSelectedGroup] = useState<StudyGroupResponse | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<'create' | 'edit'>('create');
  const [editingGroup, setEditingGroup] = useState<StudyGroupResponse | undefined>(undefined);
  const [confirmDelete, setConfirmDelete] = useState<StudyGroupResponse | null>(null);

  const fetchReferences = useCallback(async () => {
    try {
      const [coords, people, locs] = await Promise.all([
        loadAllCoordinates(),
        loadAllPersons(),
        loadAllLocations(),
      ]);
      setCoordinates(coords);
      setPersons(people);
      setLocations(locs);
    } catch (error) {
      console.warn('Не удалось загрузить справочные данные', error);
    }
  }, []);

  const fetchPage = useCallback(async () => {
    setLoadingTable(true);
    try {
      const response = await studyGroupsApi.apiV1StudyGroupsGet({
        page: paging.page - 1,
        size: paging.size,
        sortBy: paging.sortField,
        direction: paging.sortField ? paging.sortOrder : undefined,
      });
      const mapped = mapPageModel<StudyGroupResponse>(response, paging.size);
      setPagedData(mapped);
      setTableError(null);
    } catch (error: any) {
      const message = await resolveApiErrorMessage(error, 'Не удалось загрузить данные');
      setTableError(message);
      showToast(message, 'error');
    } finally {
      setLoadingTable(false);
    }
  }, [paging.page, paging.size, paging.sortField, paging.sortOrder, showToast]);

  useEffect(() => {
    fetchPage();
  }, [fetchPage]);

  useEffect(() => {
    fetchReferences();
  }, [fetchReferences]);

  useEffect(() => {
    const unsubscribe = subscribeToEntityChanges((change: EntityChange) => {
      if (change.entity === 'STUDY_GROUP') {
        fetchPage();
        if (change.action === 'DELETED') {
          const deletedId = (change.data as any)?.id;
          setSelectedGroup((current) => (current && current.id === deletedId ? null : current));
        }
      }
      if (change.entity === 'COORDINATES' || change.entity === 'PERSON' || change.entity === 'LOCATION') {
        fetchReferences();
      }
    });
    return unsubscribe;
  }, [fetchPage, fetchReferences]);

  const handleSortToggle = (field: string) => {
    setPaging((prev) => {
      const next = nextSortState(prev.sortField, prev.sortOrder, field);
      return {
        ...prev,
        page: 1,
        sortField: next.field,
        sortOrder: next.order,
      };
    });
  };

  const maxPage = pagedData.totalPages > 0 ? pagedData.totalPages : 1;

  const openCreateModal = () => {
    setFormMode('create');
    setEditingGroup(undefined);
    setFormOpen(true);
  };

  const openEditModal = (group: StudyGroupResponse) => {
    setFormMode('edit');
    setEditingGroup(group);
    setFormOpen(true);
  };

  const handleDelete = async (group: StudyGroupResponse) => {
    if (!group.id) return;
    setConfirmDelete(group);
  };

  const handleSubmit = async (
    payload: StudyGroupAddRequest | { id: number; payload: StudyGroupUpdateRequest }
  ) => {
    try {
      if ('semesterEnum' in payload) {
        await studyGroupsApi.apiV1StudyGroupsPost({ studyGroupAddRequest: payload });
        showToast('Учебная группа создана', 'success');
      } else {
        await studyGroupsApi.apiV1StudyGroupsIdPatch({
          id: payload.id,
          studyGroupUpdateRequest: payload.payload,
        });
        showToast('Учебная группа обновлена', 'success');
      }
      await fetchPage();
    } catch (error: any) {
      const message = await resolveApiErrorMessage(error, 'Не удалось сохранить учебную группу');
      showToast(message, 'error');
      return;
    }
  };

  const confirmDeleteGroup = async () => {
    if (!confirmDelete?.id) {
      setConfirmDelete(null);
      return;
    }
    try {
      await studyGroupsApi.apiV1StudyGroupsIdDelete({ id: confirmDelete.id });
      await fetchPage();
      showToast(`Группа "${confirmDelete.name}" удалена`, 'success');
    } catch (error: any) {
      const message = await resolveApiErrorMessage(error, 'Не удалось удалить учебную группу');
      showToast(message, 'error');
    } finally {
      setConfirmDelete(null);
    }
  };

  const rows = useMemo(() => pagedData.content, [pagedData]);

  return (
    <div>
      <div className="section-heading">
        <h2>Учебные группы</h2>
        <button className="primary-btn" onClick={openCreateModal}>Создать группу</button>
      </div>

      {tableError && <div className="error-banner">{tableError}</div>}

      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              <th onClick={() => handleSortToggle('id')} style={{ cursor: 'pointer' }}>
                ID {sortIndicator(paging.sortField, paging.sortOrder, 'id')}
              </th>
              <th onClick={() => handleSortToggle('name')} style={{ cursor: 'pointer' }}>
                Название {sortIndicator(paging.sortField, paging.sortOrder, 'name')}
              </th>
              <th onClick={() => handleSortToggle('course')} style={{ cursor: 'pointer' }}>
                Курс {sortIndicator(paging.sortField, paging.sortOrder, 'course')}
              </th>
              <th onClick={() => handleSortToggle('semesterEnum')} style={{ cursor: 'pointer' }}>
                Семестр {sortIndicator(paging.sortField, paging.sortOrder, 'semesterEnum')}
              </th>
              <th onClick={() => handleSortToggle('formOfEducation')} style={{ cursor: 'pointer' }}>
                Форма обучения {sortIndicator(paging.sortField, paging.sortOrder, 'formOfEducation')}
              </th>
              <th>Координаты</th>
              <th onClick={() => handleSortToggle('studentsCount')} style={{ cursor: 'pointer' }}>
                Студентов {sortIndicator(paging.sortField, paging.sortOrder, 'studentsCount')}
              </th>
              <th onClick={() => handleSortToggle('expelledStudents')} style={{ cursor: 'pointer' }}>
                Отчислено {sortIndicator(paging.sortField, paging.sortOrder, 'expelledStudents')}
              </th>
              <th onClick={() => handleSortToggle('transferredStudents')} style={{ cursor: 'pointer' }}>
                Переведено {sortIndicator(paging.sortField, paging.sortOrder, 'transferredStudents')}
              </th>
              <th onClick={() => handleSortToggle('shouldBeExpelled')} style={{ cursor: 'pointer' }}>
                Кол-во к отчислению {sortIndicator(paging.sortField, paging.sortOrder, 'shouldBeExpelled')}
              </th>
              <th onClick={() => handleSortToggle('averageMark')} style={{ cursor: 'pointer' }}>
                Средний балл {sortIndicator(paging.sortField, paging.sortOrder, 'averageMark')}
              </th>
              <th>Куратор</th>
              <th onClick={() => handleSortToggle('creationDate')} style={{ cursor: 'pointer' }}>
                Создано {sortIndicator(paging.sortField, paging.sortOrder, 'creationDate')}
              </th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {loadingTable ? (
              <tr><td colSpan={13}>Загрузка...</td></tr>
            ) : rows.length === 0 ? (
              <tr><td colSpan={13}>Нет данных</td></tr>
            ) : (
              rows.map((group) => (
                <tr key={group.id}
                    onClick={() => setSelectedGroup(group)}
                    style={{ cursor: 'pointer' }}>
                  <td>{group.id}</td>
                  <td>{group.name}</td>
                  <td>{group.course}</td>
                  <td>{group.semesterEnum}</td>
                  <td>{group.formOfEducation ?? '—'}</td>
                  <td>
                    {group.coordinates ? `X: ${group.coordinates.x}, Y: ${group.coordinates.y}` : '—'}
                  </td>
                  <td>{group.studentsCount}</td>
                  <td>{group.expelledStudents}</td>
                  <td>{group.transferredStudents}</td>
                  <td>{group.shouldBeExpelled}</td>
                  <td>{group.averageMark ?? '—'}</td>
                  <td>{group.groupAdmin?.name ?? '—'}</td>
                  <td>{formatDateTime(group.creationDate)}</td>
                  <td>
                    <div className="form-inline">
                      <button
                        type="button"
                        className="secondary-btn"
                        onClick={(event) => {
                          event.stopPropagation();
                          openEditModal(group);
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
          onClick={() => setPaging((prev) => ({ ...prev, page: Math.max(1, prev.page - 1) }))}
          disabled={paging.page === 1}
        >
          Назад
        </button>
        <span>Страница {paging.page} из {maxPage}</span>
        <button
          className="secondary-btn"
          onClick={() => setPaging((prev) => ({ ...prev, page: Math.min(maxPage, prev.page + 1) }))}
          disabled={paging.page >= maxPage}
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
          <div className="drawer-field"><strong>Курс:</strong> {selectedGroup.course}</div>
          <div className="drawer-field"><strong>Форма обучения:</strong> {selectedGroup.formOfEducation ?? '—'}</div>
          <div className="drawer-field"><strong>Координаты:</strong> X = {selectedGroup.coordinates?.x}, Y = {selectedGroup.coordinates?.y}</div>
          <div className="drawer-field"><strong>Студентов:</strong> {selectedGroup.studentsCount}</div>
          <div className="drawer-field"><strong>Отчислено:</strong> {selectedGroup.expelledStudents}</div>
          <div className="drawer-field"><strong>Переведено:</strong> {selectedGroup.transferredStudents}</div>
          <div className="drawer-field"><strong>Кол-во к отчислению:</strong> {selectedGroup.shouldBeExpelled}</div>
          <div className="drawer-field"><strong>Средний балл:</strong> {selectedGroup.averageMark ?? '—'}</div>
          <div className="drawer-field"><strong>Куратор:</strong> {selectedGroup.groupAdmin?.name ?? '—'}</div>
          <div className="drawer-field"><strong>Создано:</strong> {formatDateTime(selectedGroup.creationDate)}</div>
          <button className="primary-btn" onClick={() => openEditModal(selectedGroup)}>Редактировать</button>
        </div>
      )}

      <StudyGroupFormModal
        isOpen={formOpen}
        mode={formMode}
        initialValues={editingGroup}
        coordinates={coordinates}
        persons={persons}
        locations={locations}
        onCancel={() => setFormOpen(false)}
        onSubmit={handleSubmit}
      />

      <Modal
        open={!!confirmDelete}
        title="Удаление учебной группы"
        onClose={() => setConfirmDelete(null)}
        footer={null}
      >
        {confirmDelete && (
          <form className="form-grid" onSubmit={(event) => { event.preventDefault(); confirmDeleteGroup(); }}>
            <p>Удалить группу <strong>{confirmDelete.name}</strong>? Это действие нельзя отменить.</p>
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

export default StudyGroupsPage;
