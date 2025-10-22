import { Configuration } from './api/runtime';
import {
  CoordinatesApi,
  LocationsApi,
  PersonsApi,
  StudyGroupsApi,
} from './api/apis';

const basePath = 'http://localhost:8041';

const configuration = new Configuration({ basePath });

export const coordinatesApi = new CoordinatesApi(configuration);
export const locationsApi = new LocationsApi(configuration);
export const personsApi = new PersonsApi(configuration);
export const studyGroupsApi = new StudyGroupsApi(configuration);
