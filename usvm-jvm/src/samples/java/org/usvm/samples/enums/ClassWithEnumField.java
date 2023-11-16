package org.usvm.samples.enums;

import org.usvm.samples.enums.ClassWithEnum.StatusEnum;

public class ClassWithEnumField {
    // Make it public for simpler extracting in tests
    public StatusEnum statusEnum = null;

    public void setStatusEnum(StatusEnum statusEnum) {
        this.statusEnum = statusEnum;
    }

    public int getStatusCode(int initField) {
        ClassWithEnumField classWithEnumField = new ClassWithEnumField();
        if (initField == -1) {
            classWithEnumField.setStatusEnum(StatusEnum.READY);
        } else if (initField == 1) {
            classWithEnumField.setStatusEnum(StatusEnum.ERROR);
        }

        return classWithEnumField.statusEnum.code;
    }
}
