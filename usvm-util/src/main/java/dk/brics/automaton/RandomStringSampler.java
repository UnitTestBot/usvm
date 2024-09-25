package dk.brics.automaton;

import java.util.*;

/**
 * Generates given amount of random strings with a given length accepted by a given automaton.
 */
public class RandomStringSampler implements Iterator<String> {
    private Random random;
    private SamplingState initialState;
    private boolean sampledEmptyString = false;

    private class SamplingState {
        private State state;
        private int depth;
        private final HashSet<Transition> bannedTransitions = new HashSet<>();
        private final HashMap<Transition, HashSet<Character>> bannedChars = new HashMap<>();
        private final HashMap<Pair<Transition, Character>, SamplingState> children = new HashMap<>();

        SamplingState(State s, int depth) {
            this.state = s;
            this.depth = depth;
        }

        boolean isDead() {
            return depth == 0 || state.transitions.size() == bannedTransitions.size();
        }

        void banChar(Transition t, char c) {
            HashSet<Character> charBanSet = bannedChars.get(t);
            // charBanSet can't be null because should have been initialized before
            charBanSet.add(c);
            if (charBanSet.size() == t.max - t.min + 1) {
                bannedTransitions.add(t);
            }
        }

        private Transition getRandomTransition() {
            int randomIndex = random.nextInt(state.transitions.size());
            int i = 0;
            for (Transition t : state.transitions) {
                if (i++ == randomIndex) {
                    return t;
                }
            }
            throw new RuntimeException("Unreachable");
        }

        private SamplingState expand(Transition t, char c) {
            Pair<Transition, Character> key = new Pair<>(t, c);
            return children.computeIfAbsent(key, k -> new SamplingState(t.to, depth - 1));
        }

        boolean trySampleTo(StringBuilder sb) {
            if (depth == 0) {
                return state.accept;
            }

            while (!isDead()) {
                Transition randomTransition = getRandomTransition();
                if (bannedTransitions.contains(randomTransition)) {
                    continue;
                }

                HashSet<Character> bannedCharSet = bannedChars.computeIfAbsent(randomTransition, k -> new HashSet<>());
                do {
                    char randomChar = (char) random.nextInt(randomTransition.min, randomTransition.max + 1);
                    if (bannedCharSet.contains(randomChar)) {
                        continue;
                    }
                    sb.append(randomChar);
                    SamplingState child = expand(randomTransition, randomChar);
                    boolean samplingWorkedOut = child.trySampleTo(sb);
                    if (child.isDead()) {
                        banChar(randomTransition, randomChar);
                    }
                    if (samplingWorkedOut) {
                        return true;
                    }
                    sb.setLength(sb.length() - 1);
                } while (bannedCharSet.size() != randomTransition.max - randomTransition.min + 1);
            }
            return false;
        }
    }

    private final StringBuilder path = new StringBuilder();

    public RandomStringSampler(Automaton a, int length, Random random) {
        if (length < 0) {
            throw new IllegalArgumentException("Length should be non-negative");
        }
        this.initialState = new SamplingState(a.initial, length);
        this.random = random;
    }

    @Override
    public boolean hasNext() {
        path.setLength(0);
        if (initialState.depth == 0) {
            boolean result = !sampledEmptyString;
            sampledEmptyString = true;
            return result;
        }
        return initialState.trySampleTo(path);
    }

    @Override
    public String next() {
        return path.toString();
    }
}
