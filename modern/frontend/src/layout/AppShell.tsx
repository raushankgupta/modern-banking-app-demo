import {
  Content,
  Header,
  HeaderContainer,
  HeaderGlobalAction,
  HeaderGlobalBar,
  HeaderMenuButton,
  HeaderName,
  SideNav,
  SideNavItems,
  SideNavLink,
  SkipToContent,
} from '@carbon/react';
import { Dashboard, Logout } from '@carbon/icons-react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { screens } from './navItems';

export function AppShell() {
  const { session, logout } = useAuth();
  const { pathname } = useLocation();

  return (
    <HeaderContainer
      render={({ isSideNavExpanded, onClickSideNavExpand }) => (
        <>
          <Header aria-label="Modern Banking">
            <SkipToContent />
            <HeaderMenuButton
              aria-label={isSideNavExpanded ? 'Close menu' : 'Open menu'}
              onClick={onClickSideNavExpand}
              isActive={isSideNavExpanded}
              isCollapsible
            />
            <HeaderName as={Link} to="/" prefix="">
              Modern Banking
            </HeaderName>
            <HeaderGlobalBar>
              <span className="app-user">{session?.employeeName}</span>
              <HeaderGlobalAction aria-label="Log out" tooltipAlignment="end" onClick={logout}>
                <Logout size={20} />
              </HeaderGlobalAction>
            </HeaderGlobalBar>
            <SideNav
              aria-label="Main navigation"
              expanded={isSideNavExpanded}
              onSideNavBlur={isSideNavExpanded ? onClickSideNavExpand : undefined}
              isPersistent
            >
              <SideNavItems>
                <SideNavLink as={Link} to="/" renderIcon={Dashboard} isActive={pathname === '/'}>
                  Dashboard
                </SideNavLink>
                {screens.map((screen) => (
                  <SideNavLink
                    key={screen.to}
                    as={Link}
                    to={screen.to}
                    isActive={pathname.startsWith(screen.to)}
                  >
                    {screen.label}
                  </SideNavLink>
                ))}
              </SideNavItems>
            </SideNav>
          </Header>
          <Content id="main-content" className="app-content">
            <Outlet />
          </Content>
        </>
      )}
    />
  );
}
