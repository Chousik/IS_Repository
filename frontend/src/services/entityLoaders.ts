import { coordinatesApi, locationsApi, personsApi, studyGroupsApi } from '../apiClient';
import type {
  CoordinatesResponse,
  LocationResponse,
  PersonResponse,
  StudyGroupResponse,
} from '../api/models';
import { mapPageModel } from '../utils/pagination';

const PAGE_SIZE = 100;

async function loadPaged<T>(fetcher: (page: number) => Promise<any>): Promise<T[]> {
  const result: T[] = [];
  let page = 0;
  while (true) {
    const response = await fetcher(page);
    const mapped = mapPageModel<T>(response, PAGE_SIZE);
    result.push(...mapped.content);
    page += 1;
    if (page >= mapped.totalPages || mapped.totalPages === 0) {
      break;
    }
  }
  return result;
}

export const loadAllCoordinates = () =>
  loadPaged<CoordinatesResponse>((page) => coordinatesApi.apiV1CoordinatesGet({ page, size: PAGE_SIZE }));

export const loadAllLocations = () =>
  loadPaged<LocationResponse>((page) => locationsApi.apiV1LocationsGet({ page, size: PAGE_SIZE }));

export const loadAllPersons = () =>
  loadPaged<PersonResponse>((page) => personsApi.apiV1PersonsGet({ page, size: PAGE_SIZE }));

export const loadAllStudyGroups = () =>
  loadPaged<StudyGroupResponse>((page) => studyGroupsApi.apiV1StudyGroupsGet({ page, size: PAGE_SIZE }));
