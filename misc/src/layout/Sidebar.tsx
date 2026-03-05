import React, { useState, useEffect, useCallback, useRef } from "react";
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
  userGuide: { title: string; path: string; sections: Section[] };
  devGuide: { title: string; path: string; sections: Section[] };
}

const BREAKPOINT_MOBILE = 768;
const BREAKPOINT_TABLET = 1024;
const TRANSITION_DURATION_MS = 300;

function deriveState(width: number): { expanded: boolean; isMobile: boolean } {
  return {
    isMobile: width <= BREAKPOINT_MOBILE,
    expanded: width > BREAKPOINT_TABLET,
  };
}

const Sidebar: React.FC = () => {
  const location = useLocation();
  const config = navigationConfig as unknown as NavigationConfig;

  const initialWidth = typeof window !== "undefined" ? window.innerWidth : 1280;
  const initial = deriveState(initialWidth);

  const [expanded, setExpanded] = useState<boolean>(initial.expanded);
  const [isMobile, setIsMobile] = useState<boolean>(initial.isMobile);

  const isMobileRef = useRef<boolean>(initial.isMobile);
  const isTransitioning = useRef<boolean>(false);
  const transitionTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const ro = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width ?? window.innerWidth;
      const next = deriveState(width);
      const wasMobile = isMobileRef.current;

      isMobileRef.current = next.isMobile;
      setIsMobile(next.isMobile);
      setExpanded((prev) => {
        if (next.isMobile) return false;
        if (wasMobile && !next.isMobile) return next.expanded;
        if (width > BREAKPOINT_TABLET) return prev;
        return false;
      });
    });
    ro.observe(document.documentElement);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    return () => {
      if (transitionTimer.current) clearTimeout(transitionTimer.current);
    };
  }, []);

  const handleToggle = useCallback(() => {
    if (isTransitioning.current) return;

    isTransitioning.current = true;
    setExpanded((v) => !v);

    transitionTimer.current = setTimeout(() => {
      isTransitioning.current = false;
    }, TRANSITION_DURATION_MS);
  }, []);

  const handleBackdropClick = useCallback(() => {
    if (isTransitioning.current) return;
    isTransitioning.current = true;
    setExpanded(false);
    transitionTimer.current = setTimeout(() => {
      isTransitioning.current = false;
    }, TRANSITION_DURATION_MS);
  }, []);

  const handleLinkClick = useCallback(() => {
    if (isMobileRef.current) setExpanded(false);
  }, []);

  const getIcon = (iconName?: string): React.ReactElement => {
    const icons: Record<string, React.ReactElement> = {
      cpu: <Cpu size={15} />,
      memory: <MemoryStick size={15} />,
      code: <Code size={15} />,
      file: <FileText size={15} />,
    };
    return icons[iconName ?? "file"] ?? <FileText size={15} />;
  };

  const renderSectionList = (sections: Section[]) => (
    <ul className="app-sidebar__section-list">
      {sections.map((section) => (
        <li key={section.path} className="app-sidebar__section-item">
          <Link
            to={section.path}
            className={`app-sidebar__section-link ${
              location.pathname === section.path ? "active" : ""
            }`}
            title={section.description ?? section.title}
            onClick={handleLinkClick}
          >
            <span className="app-sidebar__section-icon">
              {getIcon(section.icon)}
            </span>
            <span className="app-sidebar__section-text">{section.title}</span>
          </Link>
        </li>
      ))}
    </ul>
  );

  return (
    <>
      <div
        className={`app-sidebar-wrapper ${
          expanded
            ? "app-sidebar-wrapper--expanded"
            : "app-sidebar-wrapper--collapsed"
        }`}
      >
        <aside className="app-sidebar">
          <div className="app-sidebar__header">
            <h3 className="app-sidebar__title">Navigation</h3>
          </div>

          <div className="app-sidebar__section-group">
            <div className="app-sidebar__group">
              <div className="app-sidebar__group-divider" />
              <h4 className="app-sidebar__group-title">User Guide</h4>
              {renderSectionList(config.userGuide.sections)}
            </div>
            <div className="app-sidebar__group">
              <div className="app-sidebar__group-divider" />
              <h4 className="app-sidebar__group-title">Developer Guide</h4>
              {renderSectionList(config.devGuide.sections)}
            </div>
          </div>
        </aside>

        <button
          className="app-sidebar__toggle-button"
          onClick={handleToggle}
          aria-label={expanded ? "Collapse sidebar" : "Expand sidebar"}
        >
          {expanded ? <ChevronLeft size={13} /> : <ChevronRight size={13} />}
        </button>
      </div>

      {isMobile && expanded && (
        <div
          className="app-sidebar__backdrop"
          onClick={handleBackdropClick}
          aria-hidden="true"
        />
      )}
    </>
  );
};

export default Sidebar;
