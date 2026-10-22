import { useState } from 'react';
import { Link } from 'react-router-dom';
import heroImg from '../assets/box-glow.jpg';
import openImg from '../assets/box-open.jpg';
import closedImg from '../assets/box-closed.jpg';
import Icon from '../components/Icon.jsx';
import Logo from '../components/Logo.jsx';
import { Button, FormError, TextField } from '../components/ui.jsx';
import { PublicApi } from '../lib/api.js';
import { isEmail } from '../lib/validators.js';

const TEAM = [
  ['DA', 'Douglas Alves Silva'],
  ['EG', 'Eduardo Gonçalves de Souza Silva'],
  ['LA', 'Lucas de Andrade Bombonato'],
  ['PM', 'Pedro Menesez Martinengue Gonçalves Fontes'],
  ['WT', 'Weverton Tevez de Nins dos Santos'],
];

const FAQ = [
  ['E se a internet ou a energia cair?', 'Os horários ficam salvos na caixa e no celular, então o alarme continua tocando sem internet. A caixa funciona na tomada ou na bateria (cerca de 12 horas) e avisa quando ela chega a 20%.'],
  ['Quem pode ver os meus dados?', 'Só quem você convidar. Você escolhe se a pessoa apenas visualiza ou também gerencia, e pode retirar o acesso quando quiser. Tudo segue a LGPD.'],
  ['A caixa sabe que eu tomei o remédio?', 'Ela sabe que o compartimento foi aberto. Para registrar a dose e baixar o estoque, você confirma no app, por toque ou voz. Assim o histórico fica correto.'],
  ['Funciona no iPhone?', 'O aplicativo é para Android 7.0 ou superior. No iPhone, você usa o site pelo navegador Safari.'],
];

function ContactForm() {
  const [f, setF] = useState({ name: '', email: '', message: '' });
  const [errors, setErrors] = useState({});
  const [state, setState] = useState({ loading: false, done: false, error: null });
  async function submit(e) {
    e.preventDefault();
    const er = {};
    if (f.name.trim().length < 2) er.name = 'Informe o seu nome.';
    if (!isEmail(f.email)) er.email = 'Informe um e-mail válido.';
    if (f.message.trim().length < 5) er.message = 'Escreva a sua mensagem.';
    setErrors(er);
    if (Object.keys(er).length) return;
    setState({ loading: true, done: false, error: null });
    try {
      await PublicApi.contact(f.name.trim(), f.email.trim(), f.message.trim());
      setState({ loading: false, done: true, error: null });
      setF({ name: '', email: '', message: '' });
    } catch (err) {
      setState({ loading: false, done: false, error: err });
    }
  }
  return (
    <form className="contact" onSubmit={submit} noValidate id="contato">
      <h3>Fale com a equipe</h3>
      {state.done && <div className="banner success" role="status"><Icon name="check" size={20} /><span className="grow">Mensagem enviada. Obrigado pelo contato!</span></div>}
      <FormError error={state.error} />
      <TextField label="Nome" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} error={errors.name} placeholder="Seu nome" maxLength={120} />
      <TextField label="E-mail" type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} error={errors.email} placeholder="voce@email.com" />
      <div className="field">
        <label htmlFor="ct-msg">Mensagem</label>
        <textarea id="ct-msg" className="textarea" rows={3} value={f.message} onChange={(e) => setF({ ...f, message: e.target.value })} placeholder="Como podemos ajudar?" maxLength={2000} aria-invalid={errors.message ? 'true' : undefined} />
        {errors.message && <p className="field-error">{errors.message}</p>}
      </div>
      <Button type="submit" size="lg" loading={state.loading}>Enviar mensagem</Button>
    </form>
  );
}

export default function Landing() {
  const [menu, setMenu] = useState(false);
  const close = () => setMenu(false);
  return (
    <div className="lp">
      <header className="lp-header">
        <div className="container">
          <Logo />
          <nav className={`lp-nav${menu ? ' open' : ''}`} aria-label="Seções da página">
            <a href="#como-funciona" onClick={close}>Como funciona</a>
            <a href="#recursos" onClick={close}>Recursos</a>
            <a href="#quem-somos" onClick={close}>Quem somos</a>
            <a href="#duvidas" onClick={close}>Dúvidas</a>
          </nav>
          <div className="lp-actions">
            <Link className="btn btn-outline btn-sm" to="/entrar">Entrar</Link>
            <Link className="btn btn-primary btn-sm" to="/cadastro">Criar minha conta</Link>
            <button type="button" className="btn btn-ghost btn-sm lp-burger" aria-label={menu ? 'Fechar menu' : 'Abrir menu'} aria-expanded={menu} onClick={() => setMenu(!menu)}>
              <Icon name={menu ? 'x' : 'menu'} size={22} />
            </button>
          </div>
        </div>
      </header>

      <main>
        <section className="hero">
          <div className="container">
            <div>
              <span className="hero-tag">Caixa inteligente + aplicativo + painel</span>
              <h1>Cada remédio na hora certa. Quem cuida de você, por perto.</h1>
              <p className="lead">O CronoMed une uma caixa de medicamentos com alarme, um aplicativo e um painel compartilhado. Você lembra, confirma e quem você escolher acompanha.</p>
              <div className="hero-ctas">
                <Link className="btn btn-primary btn-lg" to="/cadastro">Criar minha conta</Link>
                <a className="btn btn-outline btn-lg" href="#como-funciona">Ver como funciona</a>
              </div>
              <div className="checks">
                <span><Icon name="check" size={18} /> Alarmes funcionam sem internet</span>
                <span><Icon name="check" size={18} /> Dados protegidos pela LGPD</span>
                <span><Icon name="check" size={18} /> Você decide quem acompanha</span>
              </div>
            </div>
            <div className="hero-art">
              <div className="frame"><img src={heroImg} alt="Caixa CronoMed com um compartimento aceso na hora da dose" /></div>
              <div className="notif" aria-hidden="true">
                <span className="bell"><Icon name="bell" size={22} /></span>
                <span className="txt"><strong>Omeprazol 20mg · Compartimento 1</strong>Você tomou o medicamento?</span>
                <span className="yes">Sim</span>
              </div>
            </div>
          </div>
        </section>

        <section className="bg-faint">
          <div className="container">
            <p className="eyebrow">A realidade em casa</p>
            <h2 className="title">Esquecer uma dose é mais comum do que parece.</h2>
            <p className="sub">Quem toma vários remédios convive com esse risco todos os dias.</p>
            <div className="cards-3">
              <div className="lcard"><span className="ico-box red"><Icon name="pill" size={24} /></span><h3>Muitos remédios, muitos horários</h3><p>Quem trata várias doenças toma vários remédios por dia. É fácil trocar um horário ou repetir uma dose sem perceber.</p></div>
              <div className="lcard"><span className="ico-box red"><Icon name="user" size={24} /></span><h3>Morar sozinho aumenta o risco</h3><p>Idosos que moram sozinhos têm cerca de 3,7 vezes mais chance de não seguir o tratamento do jeito certo.</p></div>
              <div className="lcard"><span className="ico-box red"><Icon name="alert" size={24} /></span><h3>A família só descobre depois</h3><p>Sem registro, cuidadores e médicos não sabem o que foi tomado. Erros de dose podem acabar em internação por intoxicação.</p></div>
            </div>
            <p className="source">Fontes: Manso et al. (2018); Revista de Saúde Pública, USP (2021).</p>
          </div>
        </section>

        <section id="como-funciona">
          <div className="container">
            <p className="eyebrow">Como o CronoMed ajuda</p>
            <h2 className="title">Uma caixa que lembra. Um app que confirma. Um painel que une quem cuida.</h2>
            <p className="sub">Tudo conectado, simples de usar e pensado para quem não pode errar na medicação.</p>
            <div className="cards-3">
              {[
                ['1', 'Guarde', 'Cada remédio ganha um compartimento com cor, número e relevo. Você cadastra pelo site ou pelo app.'],
                ['2', 'Receba o alarme', 'Na hora da dose, a caixa toca e acende na cor do compartimento. O celular avisa junto.'],
                ['3', 'Confirme e compartilhe', 'Confirme por toque ou voz. O histórico e o estoque aparecem no painel de quem você autorizar.'],
              ].map(([n, t, d]) => (
                <div key={n} className="lcard"><span className="step-no">{n}</span><h3>{t}</h3><p>{d}</p></div>
              ))}
            </div>
          </div>
        </section>

        <section className="bg-soft" id="recursos">
          <div className="container">
            <p className="eyebrow">O que você ganha no dia a dia</p>
            <h2 className="title">Menos preocupação. Mais controle. A tecnologia a seu favor.</h2>
            <div className="cards-4">
              {[
                ['bell', 'Alarmes que não falham', 'O horário fica salvo na caixa e no celular. O alarme toca mesmo sem internet.'],
                ['eye', 'Acessível de verdade', 'Leitor de tela, voz e alto contraste. Cada compartimento tem cor, número e relevo em Braille.'],
                ['box', 'Estoque sem surpresa', 'Saiba quanto resta e seja avisado antes de acabar. A baixa só ocorre quando você confirma.'],
                ['users', 'Família e médicos por perto', 'Compartilhe o painel com quem você escolher. Você define o acesso e pode revogar quando quiser.'],
              ].map(([i, t, d]) => (
                <div key={t} className="lcard"><span className="ico-box blue"><Icon name={i} size={24} /></span><h3>{t}</h3><p>{d}</p></div>
              ))}
            </div>
          </div>
        </section>

        <section>
          <div className="container">
            <p className="eyebrow">A caixa</p>
            <h2 className="title">Cada remédio no seu lugar. Fácil de achar, até sem enxergar bem.</h2>
            <p className="sub">Cor, número, forma e relevo em Braille: você escolhe o compartimento certo de olhos abertos ou fechados.</p>
            <div className="box-pair">
              <figure><div className="frame"><img src={closedImg} alt="Caixa CronoMed fechada, com seis tampas coloridas e numeradas" /></div><figcaption>Seis compartimentos, cada um com cor, número e forma</figcaption></figure>
              <figure><div className="frame"><img src={openImg} alt="Caixa CronoMed com um compartimento aberto" /></div><figcaption>Abra só o compartimento da dose e feche em seguida</figcaption></figure>
            </div>
            <div className="cards-3" style={{ marginTop: 32 }}>
              <div className="feat"><Icon name="check" size={18} /><div><strong>Cor, número e forma</strong><span>Cada compartimento tem um símbolo próprio. Ninguém depende só da cor.</span></div></div>
              <div className="feat"><Icon name="check" size={18} /><div><strong>Braille em cada tampa</strong><span>O relevo permite achar o remédio certo pelo toque.</span></div></div>
              <div className="feat"><Icon name="check" size={18} /><div><strong>Luz na cor certa</strong><span>Na hora da dose, o compartimento acende e a caixa toca.</span></div></div>
            </div>
          </div>
        </section>

        <section className="stats">
          <div className="container">
            <p className="eyebrow light">Por que isso importa</p>
            <h2 className="title">O Brasil está envelhecendo. O cuidado em casa precisa acompanhar.</h2>
            <div className="cards-4">
              <div className="stat"><b>15,6%</b><p>da população brasileira tem 60 anos ou mais (IBGE, 2023). Em 2000, eram 8,7%.</p></div>
              <div className="stat"><b>20,5%</b><p>é a projeção para 2033.</p></div>
              <div className="stat"><b>3,7×</b><p>mais chance de não seguir o tratamento entre idosos que moram sozinhos.</p></div>
              <div className="stat"><b>17%</b><p>da população de Praia Grande é idosa, cerca de 65 mil pessoas (2025).</p></div>
            </div>
            <div className="cards-3" style={{ marginTop: 28 }}>
              {[1, 2, 3].map((n) => (
                <div key={n} className="quote"><strong>[Depoimento]</strong><p>“Frase curta sobre o que mudou na rotina de medicação.”</p><small>[Nome] · [Perfil: idoso, familiar ou cuidador]</small></div>
              ))}
            </div>
            <p className="fine">Substituir os depoimentos pelos relatos das sessões de teste de usabilidade. Fontes: IBGE (2024); Manso et al. (2018); Prefeitura de Praia Grande (2025).</p>
          </div>
        </section>

        <section id="quem-somos">
          <div className="container about">
            <div>
              <p className="eyebrow left">Quem somos</p>
              <h2>Nascemos em Praia Grande, para a nossa região.</h2>
              <p>O CronoMed é um projeto do curso de Desenvolvimento de Software Multiplataforma da FATEC de Praia Grande. Partimos de um problema real da Baixada Santista: em casa, tomar os remédios no horário ainda depende da memória de cada pessoa.</p>
              <p>Por isso criamos uma solução completa, com caixa, aplicativo e painel, e levamos a privacidade a sério.</p>
              <ul>
                <li><Icon name="shield" size={20} /> Conformidade com a LGPD</li>
                <li><Icon name="lock" size={20} /> Comunicação criptografada de ponta a ponta</li>
                <li><Icon name="users" size={20} /> Compartilhamento só com o seu consentimento</li>
              </ul>
            </div>
            <div className="card team">
              <h3>Equipe</h3>
              <p className="inst">FATEC Praia Grande · Centro Paula Souza</p>
              <ul>{TEAM.map(([i, n]) => <li key={n}><span className="avatar">{i}</span>{n}</li>)}</ul>
            </div>
          </div>
        </section>

        <section className="cta">
          <div className="container">
            <div>
              <h2>Comece hoje a organizar os remédios com tranquilidade.</h2>
              <p>Crie sua conta em poucos minutos e cadastre o primeiro medicamento. Se preferir, fale com a nossa equipe.</p>
              <div className="btns">
                <Link className="btn btn-white btn-lg" to="/cadastro">Criar minha conta</Link>
                <Link className="btn btn-ghost-light btn-lg" to="/entrar">Já tenho conta</Link>
              </div>
            </div>
            <ContactForm />
          </div>
        </section>

        <section className="bg-faint" id="duvidas">
          <div className="container">
            <p className="eyebrow">Dúvidas frequentes</p>
            <h2 className="title">Respostas diretas para decidir com segurança.</h2>
            <div className="faq-grid">
              {FAQ.map(([q, a]) => <div key={q} className="lcard"><h3>{q}</h3><p>{a}</p></div>)}
            </div>
          </div>
        </section>
      </main>

      <footer className="lp-footer">
        <div className="container">
          <Logo inverse />
          <nav aria-label="Rodapé">
            <a href="#quem-somos">Política de privacidade (LGPD)</a>
            <a href="#recursos">Acessibilidade</a>
            <a href="#contato">Contato</a>
          </nav>
          <p className="legal">© 2026 CronoMed · FATEC Praia Grande · Tecnologia em Desenvolvimento de Software Multiplataforma</p>
        </div>
      </footer>
    </div>
  );
}
