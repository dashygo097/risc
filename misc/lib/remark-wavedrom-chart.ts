import { visit } from "unist-util-visit";
import type { Root, Code } from "mdast";
import type { Parent } from "unist";

export function remarkWaveDromChart() {
  return (tree: Root) => {
    visit(
      tree,
      "code",
      (node: Code, index: number | undefined, parent: Parent | undefined) => {
        if (node.lang === "wavedrom" && parent && typeof index === "number") {
          parent.children[index] = {
            type: "mdxJsxFlowElement",
            name: "WaveDromChart",
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

export default remarkWaveDromChart;
