export interface NavItem {
  to: string;
  label: string;
  description: string;
}

/** The legacy main-menu screens, in the same order as the Swing MainFrame. */
export const screens: NavItem[] = [
  { to: '/search', label: 'Customer search', description: 'Find a customer or account' },
  { to: '/deposit', label: 'Cash deposit', description: 'Add funds to an account' },
  { to: '/withdraw', label: 'Cash withdrawal', description: 'Take funds from an account' },
  { to: '/transfer', label: 'Fund transfer', description: 'Move funds between accounts' },
  { to: '/history', label: 'Transaction history', description: 'Review activity on an account' },
];
