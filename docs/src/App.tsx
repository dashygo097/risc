import React, { useState } from "react";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import { Layout } from "./layout";
import HomePage from "./assets/home.mdx";
import TodoListPage from "./assets/dev-guide/todo-list.mdx";
import "./styles/global.css";

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
            <Route path="/" element={<HomePage />} />
            <Route path="/dev-guide/todo-list" element={<TodoListPage />} />
          </Routes>
        </Layout>
      </div>
    </Router>
  );
};

export default App;
