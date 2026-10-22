import { Link } from 'react-router-dom';

// Marca do CronoMed: relogio com comprimido + palavra "CronoMed" (Roboto Black 900).
export function LogoMark({ size = 34, inverse = false }) {
  const ring = inverse ? '#FFFFFF' : '#1E4F7C';
  const face = inverse ? '#FFFFFF' : '#FFFFFF';
  const hand = '#1E4F7C';
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true" focusable="false">
      <circle cx="32" cy="32" r="28" fill={face} stroke={ring} strokeWidth="6" />
      <path d="M32 15v18l10 6" fill="none" stroke={hand} strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
      <g transform="rotate(-42 46 47)">
        <rect x="35" y="41" width="22" height="13" rx="6.5" fill="#5B9BD5" stroke={hand} strokeWidth="3" />
        <path d="M46 41v13" stroke={hand} strokeWidth="2.5" />
      </g>
    </svg>
  );
}

export default function Logo({ inverse = false, to = '/', size = 34 }) {
  const content = (
    <>
      <LogoMark size={size} inverse={inverse} />
      <span className="logo-word">
        Crono<b>Med</b>
      </span>
    </>
  );
  if (!to) return <span className={`logo${inverse ? ' inverse' : ''}`}>{content}</span>;
  return (
    <Link to={to} className={`logo${inverse ? ' inverse' : ''}`} aria-label="CronoMed - início">
      {content}
    </Link>
  );
}
