import { useState } from 'react';
import CoordinatesPage from './pages/CoordinatesPage';
import LocationsPage from './pages/LocationsPage';
import PersonsPage from './pages/PersonsPage';
import StudyGroupsPage from './pages/StudyGroupsPage';
import SpecialOperationsPage from './pages/SpecialOperationsPage';
import './App.css';
import { ToastProvider } from './components/ToastProvider';

type ViewKey = 'study-groups' | 'coordinates' | 'locations' | 'persons' | 'special';

const NAV_ITEMS: { key: ViewKey; label: string }[] = [
  { key: 'study-groups', label: 'Учебные группы' },
  { key: 'coordinates', label: 'Координаты' },
  { key: 'locations', label: 'Локации' },
  { key: 'persons', label: 'Люди' },
  { key: 'special', label: 'Спецоперации' },
];

function App() {
  const [activeView, setActiveView] = useState<ViewKey>('study-groups');

  return (
    <ToastProvider>
      <div className="app-container">
        <aside className="app-sidebar">
          <div className="app-brand">Kuromi IS</div>
          <nav className="app-menu">
            {NAV_ITEMS.map((item) => (
              <button
                key={item.key}
                className={item.key === activeView ? 'menu-item active' : 'menu-item'}
                onClick={() => setActiveView(item.key)}
              >
                {item.label}
              </button>
            ))}
          </nav>
        </aside>
        <main className="app-main">
          <header className="app-header">Темная академия Kuromi</header>
          <section className="app-content">
            {activeView === 'study-groups' && <StudyGroupsPage />}
            {activeView === 'coordinates' && <CoordinatesPage />}
            {activeView === 'locations' && <LocationsPage />}
            {activeView === 'persons' && <PersonsPage />}
            {activeView === 'special' && <SpecialOperationsPage />}
          </section>
        </main>
      </div>
    </ToastProvider>
  );
}

export default App;
