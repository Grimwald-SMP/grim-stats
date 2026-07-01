import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Overview } from './pages/Overview';
import { Players } from './pages/Players';
import { PlayerDetail } from './pages/PlayerDetail';
import { Scoreboards } from './pages/Scoreboards';
import { Leaderboards } from './pages/Leaderboards';
import { Seasons, SeasonDetail } from './pages/Seasons';
import { Admin } from './pages/Admin';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Overview />} />
        <Route path="players" element={<Players />} />
        <Route path="players/:uuid" element={<PlayerDetail />} />
        <Route path="leaderboards" element={<Leaderboards />} />
        <Route path="scoreboards" element={<Scoreboards />} />
        <Route path="seasons" element={<Seasons />} />
        <Route path="seasons/:id" element={<SeasonDetail />} />
        <Route path="admin" element={<Admin />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
