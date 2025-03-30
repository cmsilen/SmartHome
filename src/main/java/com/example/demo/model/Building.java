package com.example.demo; 

class Building {

    private String name;
    private Integer id;
    private String location;
    private String[] users;
    private String admin;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }
    public String getLocation() {
        return location;
    }   
    public void setLocation(String location) {
        this.location = location;
    }
    public String[] getUsers() {
        return users;
    }
    public void setUsers(String[] users) {
        this.users = users; // Sta roba secondo me non funziona
    }
    public String getAdmin() {
        return admin;
    }
    public void setAdmin(String admin) {
        this.admin = admin;
    }
}