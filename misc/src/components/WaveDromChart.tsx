"use client";

import React, { useEffect, useRef } from "react";
import { renderErrMsg } from "../utils";

interface WavedromProps {
  children: string;
  className?: string;
}

const WaveDromChart: React.FC<WavedromProps> = ({ children, className }) => {
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
        console.error("Tip rendering error:", error);
        if (mounted && containerRef.current) {
          containerRef.current.innerHTML = renderErrMsg(
            error,
            children,
            "WaveDrom",
          );
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
        margin: "var(--space-lg) 0",
        minHeight: "50px",
        display: "flex",
        justifyContent: "center",
        borderRadius: "var(--radius-md)",
        overflow: "hidden",
      }}
    />
  );
};

export default WaveDromChart;
