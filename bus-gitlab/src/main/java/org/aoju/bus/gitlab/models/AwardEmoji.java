package org.aoju.bus.gitlab.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.aoju.bus.gitlab.utils.JacksonJson;
import org.aoju.bus.gitlab.utils.JacksonJsonEnumHelper;

import java.util.Date;

public class AwardEmoji {

    private Integer id;
    private String name;
    private org.aoju.bus.gitlab.models.User user;
    private Date createdAt;
    private Date updatedAt;
    private Integer awardableId;
    private AwardableType awardableType;

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public org.aoju.bus.gitlab.models.User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getAwardableId() {
        return awardableId;
    }

    public void setAwardableId(Integer awardableId) {
        this.awardableId = awardableId;
    }

    public AwardableType getAwardableType() {
        return awardableType;
    }

    public void setAwardableType(AwardableType awardableType) {
        this.awardableType = awardableType;
    }

    @Override
    public String toString() {
        return (JacksonJson.toJsonString(this));
    }

    public enum AwardableType {
        ISSUE, MERGE_REQUEST, NOTE, SNIPPET;

        private static JacksonJsonEnumHelper<AwardableType> enumHelper = new JacksonJsonEnumHelper<>(AwardableType.class, true);

        @JsonCreator
        public static AwardableType forValue(String value) {
            return enumHelper.forValue(value);
        }

        @JsonValue
        public String toValue() {
            return (enumHelper.toString(this));
        }

        @Override
        public String toString() {
            return (enumHelper.toString(this));
        }
    }
}
