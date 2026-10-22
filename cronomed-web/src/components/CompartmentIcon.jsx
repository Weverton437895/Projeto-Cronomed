import { compartmentStyle } from '../lib/compartments.js';

// Cor + forma + numero (a cor nunca aparece sozinha - RF16/RF17).
function shapeFor(shape, fill, stroke) {
  const common = { fill, stroke, strokeWidth: 2.6, strokeLinejoin: 'round' };
  switch (shape) {
    case 'circle':
      return <circle cx="24" cy="24" r="20" {...common} />;
    case 'square':
      return <rect x="5" y="5" width="38" height="38" rx="7" {...common} />;
    case 'triangle':
      return <path d="M24 4 45 41H3Z" {...common} />;
    case 'star':
      return <path d="m24 3 5.9 13.1 14.1 1.5-10.6 9.5 3.1 14L24 33.7 11.5 41.1l3.1-14L4 17.6l14.1-1.5Z" {...common} />;
    case 'diamond':
      return <path d="M24 3 45 24 24 45 3 24Z" {...common} />;
    case 'hexagon':
      return <path d="M14 5h20l11 19-11 19H14L3 24Z" {...common} />;
    default:
      return <circle cx="24" cy="24" r="20" {...common} />;
  }
}

export default function CompartmentIcon({ index, size = 40, label = true }) {
  const s = compartmentStyle(index);
  const textY = s.shape === 'triangle' ? 34 : s.shape === 'star' ? 28.5 : 31;
  return (
    <svg
      className="cmp"
      width={size}
      height={size}
      viewBox="0 0 48 48"
      role="img"
      aria-label={label ? `Compartimento ${index}: ${s.name}` : undefined}
      aria-hidden={label ? undefined : 'true'}
    >
      {shapeFor(s.shape, s.color, '#1A1A1A')}
      <text x="24" y={textY} textAnchor="middle" fontSize="20" fontWeight="700" fill={s.ink} fontFamily="Roboto, Arial, sans-serif">
        {index}
      </text>
    </svg>
  );
}
