package io.quarkus.domino.manifest;

import io.quarkus.domino.DependencyTreeVisitor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * This is mainly for tree verification purposes currently
 */
class TreeRecorder implements DependencyTreeVisitor {

    private long counter;
    private List<TreeNode> roots;
    private ArrayDeque<TreeNode> branch;

    @Override
    public void beforeAllRoots() {
        roots = new ArrayList<>();
        branch = new ArrayDeque<>();
        counter = 0;
    }

    List<TreeNode> getRoots() {
        return roots;
    }

    @Override
    public void afterAllRoots() {
    }

    void enterNode(DependencyVisit visit) {
        var node = TreeNode.of(Long.toString(++counter), visit.getCoords().toCompactCoords());
        var parent = branch.peek();
        if (parent == null) {
            roots.add(node);
        } else {
            parent.addChild(node);
        }
        branch.push(node);
    }

    void leaveNode(DependencyVisit visit) {
        branch.pop();
    }

    @Override
    public void enterRootArtifact(DependencyVisit visit) {
        enterNode(visit);
    }

    @Override
    public void leaveRootArtifact(DependencyVisit visit) {
        leaveNode(visit);
    }

    @Override
    public void enterDependency(DependencyVisit visit) {
        enterNode(visit);
    }

    @Override
    public void leaveDependency(DependencyVisit visit) {
        leaveNode(visit);
    }

    @Override
    public void enterParentPom(DependencyVisit visit) {
    }

    @Override
    public void leaveParentPom(DependencyVisit visit) {
    }

    @Override
    public void enterBomImport(DependencyVisit visit) {
    }

    @Override
    public void leaveBomImport(DependencyVisit visit) {
    }
}
