import React from "react";
import Header from "./Header";
import Sidebar from "./Sidebar";
import "@styles/layout/layout.css";

interface LayoutProps {
  children?: React.ReactNode;
  theme?: "light" | "dark";
  onThemeToggle?: () => void;
}

const Layout: React.FC<LayoutProps> = ({
  children,
  theme = "light",
  onThemeToggle,
}) => {
  return (
    <div className="app-layout">
      <Header theme={theme} onThemeToggle={onThemeToggle} />

      <div className="app-layout__container">
        <Sidebar />

        <main className="app-layout__main">
          <div className="app-layout__content">{children}</div>
        </main>
      </div>
    </div>
  );
};

export default Layout;
