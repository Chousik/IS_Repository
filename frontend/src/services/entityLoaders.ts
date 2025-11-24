import { coordinatesApi, locationsApi, personsApi, studyGroupsApi } from '../apiClient';
import type {
  CoordinatesResponse,
  LocationResponse,
  PersonResponse,
  StudyGroupResponse,
} from '../api/models';
import { mapPageModel } from '../utils/pagination';
import { ResponseError, FetchError } from '../api/runtime';

const PAGE_SIZE = 100;

function isResponseError(error: unknown): error is { response: Response } {
  return typeof error === 'object' && error !== null && 'response' in error;
}

async function loadPaged<T>(fetcher: (page: number) => Promise<any>): Promise<T[]> {
  const result: T[] = [];
  let page = 0;
  while (true) {
    let response: any;
    try {
      response = await fetcher(page);
    } catch (error) {
      if (isResponseError(error)) {
        const status = (error as any).response?.status;
        if (status === 404 || status === 400 || status === 204) {
          break;
        }
        let body = '';
        try {
          body = await (error as any).response.text();
        } catch (e) {
          body = '';
        }
        throw new Error(`Не удалось получить данные страницы ${page + 1}: ${status ?? 'unknown status'} ${body}`.trim());
      }
      if (error instanceof ResponseError && error.response) {
        const status = error.response.status;
        if (status === 404 || status === 400 || status === 204) {
          break;
        }
        let body = '';
        try {
          body = await error.response.text();
        } catch (e) {
          body = '';
        }
        throw new Error(`Не удалось получить данные страницы ${page + 1}: ${status} ${body}`.trim());
      }
      if (error instanceof FetchError) {
        const causeMessage = error.cause?.message ?? error.message;
        throw new Error(`Не удалось подключиться к серверу. ${causeMessage}`.trim());
      }
      throw new Error(`Не удалось получить данные страницы ${page + 1}: ${(error as Error).message}`);
    }
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
