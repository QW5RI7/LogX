package io.github.qw5ri7.logx.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {
    @Serial
    private static final long serialVersionUID = 6366727677988044827L;
    private Integer id;
    private String username;
    private String accessToken;
    private String refreshToken;
    private String uuid;
    private AccountType accountType;
}
