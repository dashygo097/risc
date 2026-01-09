"use client";

import React, { useEffect, useRef } from "react";
import { renderToString } from "react-dom/server";
import { renderErrMsg } from "../utils";
import { InfoIcon } from "./icon";

interface TipProps {
  children: string;
  className?: string;
}

const InfoBox: React.FC<TipProps> = ({ children, className }) => {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let mounted = true;

    const renderTip = async () => {
      try {
        if (!mounted || !containerRef.current) return;

        const iconSVG = renderToString(<InfoIcon size={24} />);

        const tipText = children.trim();
        containerRef.current.innerHTML = `
          <div style="
            padding: 1rem;
            border-left: 4px solid #06b6d4;
            border-radius: 0.5rem;
            font-family: system-ui, -apple-system, sans-serif;
            box-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
          ">
            <div style="
              display: flex;
              align-items: flex-start;
              gap: 0.75rem;
            ">
              ${iconSVG}
              <div style="flex: 1;">
                <div style="
                  font-weight: 700;
                  margin-bottom: 0.5rem;
                  color: #0e7490;
                  font-size: 0.9375rem;
                ">
                  Info
                </div>
                <div style="
                  font-size: 0.875rem;
                  color: #164e63;
                  white-space: pre-wrap;
                  word-wrap: break-word;
                  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                  line-height: 1.6;
                ">${tipText}</div>
              </div>
            </div>
          </div>
        `;
      } catch (error) {
        console.error("Tip rendering error:", error);
        if (mounted && containerRef.current) {
          containerRef.current.innerHTML = renderErrMsg(error, children, "Tip");
        }
      }
    };

    renderTip();

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

export default InfoBox;
