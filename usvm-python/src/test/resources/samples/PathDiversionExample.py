#  we probably won't approximate pickle, so this is used for testing path diversion detection
import pickle


def pickle_path_diversion(x: int):
    y = pickle.loads(pickle.dumps(x))  # y is equal to x
    if y >= 0:
        if x >= 0:
            return 1
        return 2  # unreachable
    else:
        if x >= 0:
            return 3  # unreachable
        return 4
