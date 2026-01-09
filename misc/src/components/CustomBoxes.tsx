import React, { useEffect, useRef } from "react";
import { renderToString } from "react-dom/server";
import { renderErrMsg } from "../utils";
import { InfoIcon, TipIcon, SuccessIcon } from "./icon";

export interface CustomBoxProps {
  children: string;
  icon: React.ReactElement;
  title: string;
  accentColor: string;
  borderColor?: string;
  background?: string;
  className?: string;
}

export const CustomBox: React.FC<CustomBoxProps> = ({
  children,
  icon,
  title,
  accentColor,
  borderColor = accentColor,
  background,
  className,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let mounted = true;
    const renderTip = async () => {
      try {
        if (!mounted || !containerRef.current) return;
        const iconSVG = renderToString(icon);
        const tipText = children.trim();
        containerRef.current.innerHTML = `
          <div style="
            padding: 1rem;
            border-left: 4px solid ${borderColor};
            border-radius: 0.5rem;
            font-family: system-ui, -apple-system, sans-serif;
            background: ${background || "white"};
            box-shadow: 0 1px 2px rgba(0,0,0,0.05);
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
                  color: ${accentColor};
                  font-size: 0.9375rem;
                ">
                  ${title}
                </div>
                <div style="
                  font-size: 0.875rem;
                  color: #444;
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
        console.error("Box rendering error:", error);
        if (mounted && containerRef.current) {
          containerRef.current.innerHTML = renderErrMsg(error, children, title);
        }
      }
    };

    renderTip();

    return () => {
      mounted = false;
    };
  }, [children, icon, title, accentColor, background, borderColor]);

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

export default CustomBox;

export const InfoBox = (
  props: Omit<CustomBoxProps, "icon" | "title" | "accentColor">,
) => (
  <CustomBox
    icon={<InfoIcon size={24} />}
    title="Info"
    accentColor="#0e7490"
    {...props}
  />
);

export const TipBox = (
  props: Omit<CustomBoxProps, "icon" | "title" | "accentColor">,
) => (
  <CustomBox
    icon={<TipIcon size={24} />}
    title="Tip"
    accentColor="#a21caf"
    background="#faf5ff"
    {...props}
  />
);

export const SuccessBox = (
  props: Omit<CustomBoxProps, "icon" | "title" | "accentColor">,
) => (
  <CustomBox
    icon={<SuccessIcon size={24} />}
    title="Success"
    accentColor="#059669"
    background="#f0fdf4"
    {...props}
  />
);
