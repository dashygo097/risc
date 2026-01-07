"use client";

import React, { useEffect, useRef } from "react";

interface WavedromProps {
  children: string;
  className?: string;
}

const Wavedrom: React.FC<WavedromProps> = ({ children, className }) => {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let mounted = true;

    const renderWaveform = async () => {
      try {
        // @ts-expect-error: Dynamic import
        const wavedrom = await import("wavedrom");

        if (!mounted || !containerRef.current) return;

        const waveformData = JSON.parse(children.trim());

        containerRef.current.innerHTML = "";

        const onmlResult = wavedrom.renderAny(
          0,
          waveformData,
          wavedrom.waveSkin,
        );

        const svgString = wavedrom.onml.stringify(onmlResult);

        if (mounted && containerRef.current) {
          containerRef.current.innerHTML = svgString;

          const svg = containerRef.current.querySelector("svg");
          if (svg) {
            svg.style.maxWidth = "100%";
            svg.style.height = "auto";
          }
        }
      } catch (error) {
        console.error("WaveDrom rendering error:", error);
        if (mounted && containerRef.current) {
          const errorMsg =
            error instanceof Error ? error.message : String(error);
          containerRef.current.innerHTML = `
            <div style="
              color: #dc2626;
              padding: 1rem;
              background: #fee2e2;
              border-radius: 0.375rem;
              border-left: 4px solid #dc2626;
              font-family: system-ui, -apple-system, sans-serif;
            ">
              <div style="font-weight: 600; margin-bottom: 0.5rem;">
                ❌ WaveDrom Rendering Error
              </div>
              <pre style="
                margin: 0;
                font-size: 0.875rem;
                white-space: pre-wrap;
                word-wrap:  break-word;
                font-family: 'Courier New', monospace;
                color: #991b1b;
              ">${errorMsg}</pre>
              <details style="margin-top:  0.75rem; font-size: 0.875rem;">
                <summary style="cursor: pointer; user-select: none; font-weight: 500;">
                  View Source JSON
                </summary>
                <pre style="
                  margin:  0.5rem 0 0 0;
                  padding: 0.75rem;
                  background: white;
                  border-radius:  0.25rem;
                  overflow-x: auto;
                  font-size: 0.8125rem;
                  border: 1px solid #fecaca;
                ">${children}</pre>
              </details>
            </div>
          `;
        }
      }
    };

    renderWaveform();

    return () => {
      mounted = false;
    };
  }, [children]);

  return (
    <div
      ref={containerRef}
      className={className}
      style={{
        margin: "1rem 0",
        minHeight: "50px",
        display: "flex",
        justifyContent: "center",
      }}
    />
  );
};

export default Wavedrom;
