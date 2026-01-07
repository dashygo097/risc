import { visit } from "unist-util-visit";
import type { Root } from "mdast";

export function remarkWaveDrom() {
  return (tree: Root) => {
    visit(tree, "code", (node: any, index: number, parent: any) => {
      if (node.lang === "wavedrom") {
        const value = node.value;

        parent.children[index] = {
          type: "mdxJsxFlowElement",
          name: "WaveDrom",
          attributes: [],
          children: [
            {
              type: "text",
              value: value,
            },
          ],
        };
      }
    });
  };
}

export default remarkWaveDrom;
