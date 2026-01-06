declare module "*.mdx" {
  import { ComponentType } from "react";
  const MDXComponent: ComponentType;
  export default MDXComponent;
}

declare module "*.json" {
  const value: any;
  export default value;
}
