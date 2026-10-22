// Identidade dos 6 compartimentos (RF16/RF17): cor + forma + numero. A cor nunca aparece sozinha.
export const COMPARTMENTS = {
  1: { shape: 'circle', color: '#DD4245', ink: '#FFFFFF', name: 'Círculo vermelho' },
  2: { shape: 'square', color: '#56AF62', ink: '#1A1A1A', name: 'Quadrado verde' },
  3: { shape: 'triangle', color: '#F6D331', ink: '#1A1A1A', name: 'Triângulo amarelo' },
  4: { shape: 'star', color: '#9E5CA8', ink: '#FFFFFF', name: 'Estrela roxa' },
  5: { shape: 'diamond', color: '#5B9BD5', ink: '#1A1A1A', name: 'Losango azul' },
  6: { shape: 'hexagon', color: '#EE8843', ink: '#1A1A1A', name: 'Hexágono laranja' },
};

export function compartmentStyle(index) {
  return COMPARTMENTS[index] || COMPARTMENTS[1];
}
