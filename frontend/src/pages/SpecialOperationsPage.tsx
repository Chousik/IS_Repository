import { useCallback, useEffect, useState } from 'react';
import {
  Semester,
  StudyGroupExpelledTotalResponse,
  StudyGroupResponse,
  StudyGroupShouldBeExpelledGroupResponse,
} from '../api/models';
import { studyGroupsApi } from '../apiClient';
import { loadAllStudyGroups } from '../services/entityLoaders';
import { subscribeToEntityChanges } from '../services/events';

const semesterOptions: Semester[] = ['FIRST', 'SECOND', 'FOURTH', 'SIXTH', 'SEVENTH'];

const SpecialOperationsPage = () => {
  const [studyGroups, setStudyGroups] = useState<StudyGroupResponse[]>([]);
  const [deleteAllSemester, setDeleteAllSemester] = useState<Semester>('FIRST');
  const [deleteOneSemester, setDeleteOneSemester] = useState<Semester>('FIRST');
  const [groupedData, setGroupedData] = useState<StudyGroupShouldBeExpelledGroupResponse[]>([]);
  const [totalExpelled, setTotalExpelled] = useState<StudyGroupExpelledTotalResponse | null>(null);
  const [selectedGroupId, setSelectedGroupId] = useState<number | ''>('');
  const [studentCount, setStudentCount] = useState(1);
  const [loadingState, setLoadingState] = useState<{ [key: string]: boolean }>({});

  const setLoading = (key: string, value: boolean) =>
    setLoadingState((prev) => ({ ...prev, [key]: value }));

  const refreshGroups = useCallback(async () => {
    try {
      const groups = await loadAllStudyGroups();
      setStudyGroups(groups);
    } catch (err: any) {
      console.error(err);
    }
  }, []);

  useEffect(() => {
    refreshGroups();
  }, [refreshGroups]);

  useEffect(() => {
    const unsubscribe = subscribeToEntityChanges((change) => {
      if (change.entity === 'STUDY_GROUP') {
        refreshGroups();
      }
    });
    return unsubscribe;
  }, [refreshGroups]);

  const handleDeleteAll = async () => {
    setLoading('deleteAll', true);
    try {
      await studyGroupsApi.apiV1StudyGroupsBySemesterDelete({ semesterEnum: deleteAllSemester });
      await refreshGroups();
      alert('Удаление выполнено');
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось удалить группы');
    } finally {
      setLoading('deleteAll', false);
    }
  };

  const handleDeleteOne = async () => {
    setLoading('deleteOne', true);
    try {
      await studyGroupsApi.apiV1StudyGroupsBySemesterOneDelete({ semesterEnum: deleteOneSemester });
      await refreshGroups();
      alert('Группа удалена');
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось удалить группу');
    } finally {
      setLoading('deleteOne', false);
    }
  };

  const handleGroupStats = async () => {
    setLoading('groupStats', true);
    try {
      const stats = await studyGroupsApi.apiV1StudyGroupsStatsShouldBeExpelledGet();
      setGroupedData(stats);
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось получить статистику');
    } finally {
      setLoading('groupStats', false);
    }
  };

  const handleTotalExpelled = async () => {
    setLoading('totalExpelled', true);
    try {
      const result = await studyGroupsApi.apiV1StudyGroupsStatsExpelledTotalGet();
      setTotalExpelled(result);
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось получить сумму');
    } finally {
      setLoading('totalExpelled', false);
    }
  };

  const handleAddStudent = async () => {
    if (!selectedGroupId) {
      alert('Выберите группу');
      return;
    }
    setLoading('addStudent', true);
    try {
      const group = studyGroups.find((g) => g.id === Number(selectedGroupId));
      const current = group?.studentsCount ?? 0;
      await studyGroupsApi.apiV1StudyGroupsIdPatch({
        id: Number(selectedGroupId),
        studyGroupUpdateRequest: { studentsCount: current + studentCount },
      });
      await refreshGroups();
      alert('Студенты добавлены');
    } catch (err: any) {
      alert(err?.message ?? 'Не удалось обновить группу');
    } finally {
      setLoading('addStudent', false);
    }
  };

  return (
    <div>
      <h2>Специальные операции</h2>
      <div className="special-grid">
        <div className="special-card">
          <h3>Удалить все группы по семестру</h3>
          <select
            className="select"
            value={deleteAllSemester}
            onChange={(event) => setDeleteAllSemester(event.target.value as Semester)}
          >
            {semesterOptions.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </select>
          <button className="danger-btn" onClick={handleDeleteAll} disabled={loadingState['deleteAll']}>
            {loadingState['deleteAll'] ? 'Удаление...' : 'Удалить все'}
          </button>
        </div>

        <div className="special-card">
          <h3>Удалить одну группу по семестру</h3>
          <select
            className="select"
            value={deleteOneSemester}
            onChange={(event) => setDeleteOneSemester(event.target.value as Semester)}
          >
            {semesterOptions.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </select>
          <button className="danger-btn" onClick={handleDeleteOne} disabled={loadingState['deleteOne']}>
            {loadingState['deleteOne'] ? 'Удаление...' : 'Удалить одну'}
          </button>
        </div>

        <div className="special-card">
          <h3>Группировка по shouldBeExpelled</h3>
          <button className="secondary-btn" onClick={handleGroupStats} disabled={loadingState['groupStats']}>
            {loadingState['groupStats'] ? 'Загрузка...' : 'Получить данные'}
          </button>
          <ul className="list-group">
            {groupedData.length === 0 ? (
              <li>Нет данных</li>
            ) : (
              groupedData.map((item) => (
                <li key={item.shouldBeExpelled}>
                  shouldBeExpelled = {item.shouldBeExpelled}: {item.count}
                </li>
              ))
            )}
          </ul>
        </div>

        <div className="special-card">
          <h3>Общее число отчисленных студентов</h3>
          <button className="secondary-btn" onClick={handleTotalExpelled} disabled={loadingState['totalExpelled']}>
            {loadingState['totalExpelled'] ? 'Расчёт...' : 'Рассчитать'}
          </button>
          <div style={{ fontSize: 28, fontWeight: 600 }}>
            {totalExpelled ? totalExpelled.totalExpelledStudents : '—'}
          </div>
        </div>

        <div className="special-card">
          <h3>Добавить студентов в группу</h3>
          <select
            className="select"
            value={selectedGroupId}
            onChange={(event) => setSelectedGroupId(event.target.value ? Number(event.target.value) : '')}
          >
            <option value="">Выберите группу</option>
            {studyGroups.map((group) => (
              <option key={group.id} value={group.id!}>
                #{group.id} {group.name}
              </option>
            ))}
          </select>
          <input
            className="number-input"
            type="number"
            min={1}
            value={studentCount}
            onChange={(event) => setStudentCount(Number(event.target.value))}
          />
          <button className="primary-btn" onClick={handleAddStudent} disabled={loadingState['addStudent']}>
            {loadingState['addStudent'] ? 'Добавление...' : 'Добавить'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default SpecialOperationsPage;
