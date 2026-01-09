export const renderErrMsg = (
  error: unknown,
  sourceContent?: string,
  componentName: string = "Component",
): string => {
  const errorMsg = error instanceof Error ? error.message : String(error);

  return `
    <div style="
      color: #dc2626;
      padding: 1rem;
      background: #fee2e2;
      border-radius: 0.375rem;
      border-left: 4px solid #dc2626;
      font-family: system-ui, -apple-system, sans-serif;
    ">
      <div style="font-weight: 600; margin-bottom: 0.5rem;">
        ❌ ${componentName} Rendering Error
      </div>
      <pre style="
        margin: 0;
        font-size: 0.875rem;
        white-space: pre-wrap;
        word-wrap: break-word;
        font-family: 'Courier New', monospace;
        color: #991b1b;
      ">${errorMsg}</pre>
      ${
        sourceContent
          ? `
        <details style="margin-top: 0.75rem; font-size: 0.875rem;">
          <summary style="cursor: pointer; user-select: none; font-weight: 500;">
            View Source
          </summary>
          <pre style="
            margin: 0.5rem 0 0 0;
            padding: 0.75rem;
            background: white;
            border-radius: 0.25rem;
            overflow-x: auto;
            font-size: 0.8125rem;
            border: 1px solid #fecaca;
          ">${sourceContent}</pre>
        </details>
      `
          : ""
      }
    </div>
  `;
};
