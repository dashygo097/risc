declare module "*.mdx" {
  import { ComponentType } from "react";
  const MDXComponent: ComponentType;
  export default MDXComponent;
}

declare module "*.json" {
  const value: Record<string, unknown>;
  export default value;
}
