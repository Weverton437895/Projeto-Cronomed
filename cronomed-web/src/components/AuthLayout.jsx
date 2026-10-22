import Logo from './Logo.jsx';
import boxGlow from '../assets/box-glow.jpg';

// Painel azul + formulario (telas de acesso do prototipo desktop).
export default function AuthLayout({ children, wide = false }) {
  return (
    <div className="auth">
      <a className="skip-link" href="#conteudo">Pular para o conteúdo</a>
      <aside className="auth-aside">
        <Logo inverse />
        <h2>Sua medicação organizada, no horário certo.</h2>
        <p>Alarmes na caixa e no celular, confirmação por toque ou voz e acompanhamento por quem cuida de você.</p>
        <div className="auth-art">
          <img src={boxGlow} alt="Caixa CronoMed com seis compartimentos coloridos; o compartimento 1 está aceso em vermelho." width="1100" height="728" />
        </div>
        <div className="auth-foot">© 2026 CronoMed · FATEC Praia Grande</div>
      </aside>
      <main className="auth-main" id="conteudo">
        <div className={`auth-card${wide ? ' wide' : ''}`}>{children}</div>
      </main>
    </div>
  );
}
