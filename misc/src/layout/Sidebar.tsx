import React, { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import {
  FileText,
  Cpu,
  MemoryStick,
  Code,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import navigationConfig from "@assets/config/navigation.json";
import "@styles/layout/sidebar.css";

interface Section {
  title: string;
  path: string;
  icon?: string;
  description?: string;
}

interface NavigationConfig {
  userGuide: {
    title: string;
    path: string;
    sections: Section[];
  };
  devGuide: {
    title: string;
    path: string;
    sections: Section[];
  };
}

const Sidebar: React.FC = () => {
  const [expanded, setExpanded] = useState(true);
  const location = useLocation();
  const config = navigationConfig as unknown as NavigationConfig;

  const getIcon = (iconName?: string): React.ReactElement => {
    const icons: Record<string, React.ReactElement> = {
      cpu: <Cpu size={14} />,
      memory: <MemoryStick size={14} />,
      code: <Code size={14} />,
      file: <FileText size={14} />,
    };
    return icons[iconName || "file"];
  };

  return (
    <aside
      className={`app-sidebar ${
        expanded ? "app-sidebar--expanded" : "app-sidebar--collapsed"
      }`}
    >
      <button
        className="app-sidebar__toggle-button"
        onClick={() => setExpanded(!expanded)}
        aria-label={expanded ? "Collapse sidebar" : "Expand sidebar"}
      >
        {expanded ? <ChevronLeft size={14} /> : <ChevronRight size={14} />}
      </button>

      <div className="app-sidebar__header">
        <h3 className="app-sidebar__title">Navigation</h3>
      </div>

      <div className="app-sidebar__section-group">
        <h4 className="app-sidebar__group-title">User Guide</h4>
        <ul className="app-sidebar__section-list">
          {config.userGuide.sections.map((section: Section) => (
            <li key={section.path} className="app-sidebar__section-item">
              <Link
                to={section.path}
                className={`app-sidebar__section-link ${
                  location.pathname === section.path ? "active" : ""
                }`}
                title={expanded ? section.description : section.title}
              >
                <span className="app-sidebar__section-icon">
                  {getIcon(section.icon)}
                </span>
                <span className="app-sidebar__section-text">
                  {section.title}
                </span>
              </Link>
            </li>
          ))}
        </ul>

        <h4 className="app-sidebar__group-title">Developer Guide</h4>
        <ul className="app-sidebar__section-list">
          {config.devGuide.sections.map((section: Section) => (
            <li key={section.path} className="app-sidebar__section-item">
              <Link
                to={section.path}
                className={`app-sidebar__section-link ${
                  location.pathname === section.path ? "active" : ""
                }`}
                title={expanded ? section.description : section.title}
              >
                <span className="app-sidebar__section-icon">
                  {getIcon(section.icon)}
                </span>
                <span className="app-sidebar__section-text">
                  {section.title}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </aside>
  );
};

export default Sidebar;
