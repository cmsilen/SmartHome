db.Users.find(
    {
        username: "johndoe",
        password: "password123"
    }
).explain("executionStats");