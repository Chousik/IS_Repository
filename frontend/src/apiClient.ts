import { Configuration } from './api/runtime';
import {
  CoordinatesApi,
  LocationsApi,
  PersonsApi,
  StudyGroupsApi,
} from './api/apis';

let defaultBase = '';
if (typeof window !== 'undefined') {
  if (process.env.NODE_ENV !== 'development') {
    defaultBase = window.location.origin;
  }
}
const basePath = process.env.REACT_APP_API_BASE && process.env.REACT_APP_API_BASE.length > 0
  ? process.env.REACT_APP_API_BASE
  : defaultBase;

const configuration = new Configuration({ basePath });

export const coordinatesApi = new CoordinatesApi(configuration);
export const locationsApi = new LocationsApi(configuration);
export const personsApi = new PersonsApi(configuration);
export const studyGroupsApi = new StudyGroupsApi(configuration);
