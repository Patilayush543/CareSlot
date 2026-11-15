package app.scheduler;

/** A balanced-by-priority interval search tree for half-open time ranges [start, end). */
public final class IntervalTree {
    private static final class Node {
        Appointment value;
        int maxEnd;
        int priority;
        Node left;
        Node right;

        Node(Appointment value) {
            this.value = value;
            this.maxEnd = value.endMinute;
            this.priority = value.id.hashCode();
        }
    }

    private Node root;
    private int size;

    public int size() { return size; }

    /** Returns the first appointment that overlaps [start, end), or null. */
    public Appointment findOverlap(int start, int end) {
        if (start < 0 || end > 24 * 60 || start >= end) {
            throw new IllegalArgumentException("Time range must be valid and end after start.");
        }
        Node match = find(root, start, end);
        return match == null ? null : match.value;
    }

    private Node find(Node node, int start, int end) {
        if (node == null) return null;
        if (start < node.value.endMinute && node.value.startMinute < end) return node;
        if (node.left != null && node.left.maxEnd > start) {
            Node match = find(node.left, start, end);
            if (match != null) return match;
        }
        return find(node.right, start, end);
    }

    /** Inserts an appointment, rejecting any overlapping interval. */
    public void insert(Appointment appointment) {
        if (findOverlap(appointment.startMinute, appointment.endMinute) != null) {
            throw new IllegalArgumentException("That time overlaps an existing appointment.");
        }
        root = insertNode(root, new Node(appointment));
        size++;
    }

    /** Removes an appointment by its interval-tree key and returns whether it was present. */
    public boolean remove(Appointment appointment) {
        if (appointment == null) throw new IllegalArgumentException("Appointment is required.");
        if (findByKey(root, appointment) == null) return false;
        root = removeNode(root, appointment);
        size--;
        return true;
    }

    private Node findByKey(Node node, Appointment appointment) {
        if (node == null) return null;
        int comparison = compare(appointment, node.value);
        if (comparison == 0) return node;
        return comparison < 0 ? findByKey(node.left, appointment) : findByKey(node.right, appointment);
    }

    private Node removeNode(Node node, Appointment appointment) {
        int comparison = compare(appointment, node.value);
        if (comparison < 0) {
            node.left = removeNode(node.left, appointment);
            refresh(node);
            return node;
        }
        if (comparison > 0) {
            node.right = removeNode(node.right, appointment);
            refresh(node);
            return node;
        }
        return merge(node.left, node.right);
    }

    private Node merge(Node left, Node right) {
        if (left == null) return right;
        if (right == null) return left;
        if (left.priority < right.priority) {
            left.right = merge(left.right, right);
            refresh(left);
            return left;
        }
        right.left = merge(left, right.left);
        refresh(right);
        return right;
    }

    private Node insertNode(Node node, Node added) {
        if (node == null) return added;
        if (compare(added.value, node.value) < 0) {
            node.left = insertNode(node.left, added);
            if (node.left.priority < node.priority) node = rotateRight(node);
        } else {
            node.right = insertNode(node.right, added);
            if (node.right.priority < node.priority) node = rotateLeft(node);
        }
        refresh(node);
        return node;
    }

    private int compare(Appointment a, Appointment b) {
        int time = Integer.compare(a.startMinute, b.startMinute);
        return time != 0 ? time : a.id.compareTo(b.id);
    }

    private Node rotateRight(Node top) {
        Node next = top.left;
        top.left = next.right;
        next.right = top;
        refresh(top);
        refresh(next);
        return next;
    }

    private Node rotateLeft(Node top) {
        Node next = top.right;
        top.right = next.left;
        next.left = top;
        refresh(top);
        refresh(next);
        return next;
    }

    private void refresh(Node node) {
        node.maxEnd = node.value.endMinute;
        if (node.left != null) node.maxEnd = Math.max(node.maxEnd, node.left.maxEnd);
        if (node.right != null) node.maxEnd = Math.max(node.maxEnd, node.right.maxEnd);
    }
}
