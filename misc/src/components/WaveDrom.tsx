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
        const wavedrom = await import("wavedrom");

        if (!mounted || !containerRef.current) return;

        const waveformData = JSON.parse(children.trim());

        containerRef.current.innerHTML = "";

        let svg;
        if (wavedrom.renderAny) {
          svg = wavedrom.renderAny(0, waveformData, wavedrom.waveSkin);
        } else if (wavedrom.renderWaveForm) {
          svg = wavedrom.renderWaveForm(0, waveformData, wavedrom.waveSkin);
        } else {
          throw new Error(
            "No suitable render function found in wavedrom module",
          );
        }

        if (!svg || !(svg instanceof Element)) {
          throw new Error("Render function did not return a valid SVG element");
        }

        if (mounted && containerRef.current) {
          containerRef.current.appendChild(svg);
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
              border-radius:  0.375rem;
              border-left:  4px solid #dc2626;
              font-family: system-ui, -apple-system, sans-serif;
            ">
              <div style="font-weight: 600; margin-bottom: 0.5rem;">
                ❌ WaveDrom Rendering Error
              </div>
              <pre style="
                margin: 0;
                font-size: 0.875rem;
                white-space: pre-wrap;
                word-wrap: break-word;
                font-family: 'Courier New', monospace;
              ">${errorMsg}</pre>
              <details style="margin-top: 0.5rem; font-size: 0.875rem;">
                <summary style="cursor:  pointer; user-select: none;">View JSON</summary>
                <pre style="
                  margin: 0.5rem 0 0 0;
                  padding: 0.5rem;
                  background: white;
                  border-radius: 0.25rem;
                  overflow-x: auto;
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
      }}
    />
  );
};

export default Wavedrom;
