package io.quarkus.domino.manifest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * General tree node mostly for verification purposes
 */
class TreeNode {

    static TreeNode of(String id) {
        return new TreeNode(id, id);
    }

    static TreeNode of(String id, String name) {
        return new TreeNode(id, name);
    }

    final String id;
    final String name;
    final Map<String, TreeNode> children = new HashMap<>();

    private TreeNode(String id, String name) {
        this.id = id;
        this.name = name;
    }

    void addChild(TreeNode child) {
        if (children.put(child.name, child) != null) {
            throw new IllegalStateException("Tree node " + child.name + " has already");
        }
    }

    void isIdentical(TreeNode other) {
        isIdentical(new ArrayDeque<>(), other);
    }

    private void isIdentical(ArrayDeque<TreeNode> branch, TreeNode other) {
        if (!name.equals(other.name)) {
            throw new RuntimeException("Name mismatch: " + name + " vs " + other.name);
        }
        if (!children.keySet().equals(other.children.keySet())) {
            final StringBuilder sb = new StringBuilder();
            var i = branch.descendingIterator();
            int level = 0;
            while (i.hasNext()) {
                for (int j = 0; j < level; ++j) {
                    sb.append("  ");
                }
                sb.append(i.next().name).append(System.lineSeparator());
                ++level;
            }
            for (int j = 0; j < level; ++j) {
                sb.append("  ");
            }
            sb.append(name).append(System.lineSeparator());
            sb.append("expected direct dependencies");

            List<String> childNames = new ArrayList<>(children.keySet());
            Collections.sort(childNames);
            sb.append(System.lineSeparator());
            childNames.forEach(name -> sb.append("- ").append(name).append(System.lineSeparator()));

            childNames = new ArrayList<>(other.children.keySet());
            Collections.sort(childNames);
            sb.append("but got").append(System.lineSeparator());
            childNames.forEach(name -> sb.append("- ").append(name).append(System.lineSeparator()));
            throw new RuntimeException(sb.toString());
        }
        if (!children.isEmpty()) {
            branch.push(this);
            for (TreeNode child : children.values()) {
                child.isIdentical(branch, other.children.get(child.name));
            }
            branch.pop();
        }
    }
}
