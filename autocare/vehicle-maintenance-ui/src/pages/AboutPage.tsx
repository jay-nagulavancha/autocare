import NavBar from '../components/NavBar';
import ServiceVersionsPanel from '../components/ServiceVersionsPanel';

export default function AboutPage() {
  return (
    <>
      <NavBar />
      <main style={{ padding: '2rem', maxWidth: 720 }}>
        <h1 style={{ margin: '0 0 0.5rem', fontSize: '1.5rem', color: '#0f172a' }}>About</h1>
        <p style={{ margin: '0 0 1rem', color: '#475569', fontSize: '0.95rem' }}>
          Service build identifiers for the AutoCare demo.
        </p>
        <ServiceVersionsPanel />
      </main>
    </>
  );
}
