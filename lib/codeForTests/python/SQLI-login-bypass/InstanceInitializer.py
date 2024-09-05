class InstanceInitializer:
    value: str
    list: List[str]

    def __new__(cls, *args, **kwargs):
        instance = super().__new__(cls)
        instance.value = ""
        lst = [instance.value]
        instance.list = lst
        return instance

    def __init__(self, value):
        self.value = value
        self.list.append(value)