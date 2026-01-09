import { visit } from "unist-util-visit";
import type { Root, Code } from "mdast";
import type { Parent } from "unist";

export function remarkInfoBox() {
  return (tree: Root) => {
    visit(
      tree,
      "code",
      (node: Code, index: number | undefined, parent: Parent | undefined) => {
        if (node.lang === "info" && parent && typeof index === "number") {
          parent.children[index] = {
            type: "mdxJsxFlowElement",
            name: "InfoBox",
            attributes: [],
            children: [
              {
                type: "text",
                value: node.value,
              },
            ],
          } as unknown as Code;
        }
      },
    );
  };
}

export default remarkInfoBox;
