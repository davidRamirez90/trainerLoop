import { render, screen } from '@testing-library/react';

import { CoachPanel } from '../components/CoachPanel';

describe('CoachPanel', () => {
  it('renders coach profile and suggestions', () => {
    render(<CoachPanel compliance={0.7} strain={0.9} />);

    expect(screen.getByText('Ari Mendoza')).toBeInTheDocument();
    expect(screen.getByText('Performance Coach')).toBeInTheDocument();
    expect(
      screen.getByText('Dial back intensity for the next block and rebuild consistency.')
    ).toBeInTheDocument();
    expect(
      screen.getByText('Your strain is high. Prioritize recovery and easy volume.')
    ).toBeInTheDocument();
  });
});
