import React from "react";
import { Link, useLocation } from "react-router-dom";
import { Cpu, BookOpen, Code, Sun, Moon, Github } from "lucide-react";

import "../styles/layout/header.css";

interface HeaderProps {
  theme?: "light" | "dark";
  onThemeToggle?: () => void;
}

const Header: React.FC<HeaderProps> = ({ theme = "light", onThemeToggle }) => {
  const location = useLocation();

  const navItems = [
    { path: "/", label: "Main", icon: <BookOpen size={20} /> },
    {
      path: "/dev-guide/todo-list",
      label: "Dev",
      icon: <Code size={20} />,
    },
  ];

  return (
    <header className="app-header">
      <div className="app-header__left">
        <Link to="/" className="app-header__logo">
          <Cpu size={28} />
          <span>RISC Framework Docs</span>
        </Link>

        <nav className="app-header__nav">
          {navItems.map((item) => (
            <Link
              key={item.path}
              to={item.path}
              className={`app-header__nav-link ${
                location.pathname === item.path ? "active" : ""
              }`}
            >
              {item.icon}
              <span>{item.label}</span>
            </Link>
          ))}
        </nav>
      </div>

      <div className="app-header__right">
        {onThemeToggle && (
          <button
            className="app-header__theme-toggle"
            onClick={onThemeToggle}
            aria-label={`Switch to ${theme === "light" ? "dark" : "light"} theme`}
          >
            {theme === "light" ? <Moon size={18} /> : <Sun size={18} />}
          </button>
        )}

        <a
          href="https://github.com/dashygo097/risc.git"
          target="_blank"
          rel="noopener noreferrer"
          className="app-header__github-link"
          aria-label="View on GitHub"
        >
          <Github size={18} />
          <span>GitHub</span>
        </a>
      </div>
    </header>
  );
};

export default Header;
