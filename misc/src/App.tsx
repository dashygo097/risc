import React, { useState } from "react";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import { Layout } from "./layout";
import "@styles/global.css";

import RootPage from "@assets/root.mdx";
import UserGuidePage from "@assets/user-guide/root.mdx";
import QuickStartPage from "@assets/user-guide/getting-started.mdx";
import UsingDEMUPage from "@assets/user-guide/using-demu.mdx";
import DevGuidePage from "@assets/dev-guide/root.mdx";
import DEMULoggingPage from "@assets/dev-guide/demu-logging.mdx";
import HowToDrawWaveforms from "@assets/dev-guide/how-to-draw-waveforms.mdx";

const App: React.FC = () => {
  const [theme, setTheme] = useState<"light" | "dark">("light");

  const toggleTheme = () => {
    setTheme((prev) => (prev === "light" ? "dark" : "light"));
  };

  return (
    <Router>
      <div className={theme === "dark" ? "app-theme-dark" : ""}>
        <Layout theme={theme} onThemeToggle={toggleTheme}>
          <Routes>
            <Route path="/" element={<RootPage />} />
            <Route path="/user-guide/root" element={<UserGuidePage />} />
            <Route
              path="/user-guide/getting-started"
              element={<QuickStartPage />}
            />
            <Route path="/user-guide/using-demu" element={<UsingDEMUPage />} />
            <Route path="/dev-guide/root" element={<DevGuidePage />} />
            <Route
              path="/dev-guide/demu-logging"
              element={<DEMULoggingPage />}
            />
            <Route
              path="/dev-guide/how-to-draw-waveforms"
              element={<HowToDrawWaveforms />}
            />
          </Routes>
        </Layout>
      </div>
    </Router>
  );
};

export default App;
