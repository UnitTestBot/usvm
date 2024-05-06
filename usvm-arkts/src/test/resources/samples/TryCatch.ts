function tryCatch(): string {
    let result: string = "";

    try {
        const message: string = "An error occurred!";
        throw new Error(message);
    } catch (error) {
        result = "Caught an error: " + error.message;
    }

    return result
}
