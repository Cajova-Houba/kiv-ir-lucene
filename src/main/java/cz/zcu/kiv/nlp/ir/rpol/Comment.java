package cz.zcu.kiv.nlp.ir.rpol;

/**
 * One comment on post from /r/politics
 */
public class Comment {

    private String username;
    private String text;
    private int score;
    private String timestamp;

    public Comment(String username, String text, int score, String timestamp) {
        this.username = username;
        this.text = text;
        this.score = score;
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
