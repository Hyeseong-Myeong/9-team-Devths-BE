package com.ktb3.devths.global.ratelimit.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

	private String backend = "in-memory";
	private Redis redis = new Redis();
	private GoogleCalendarLimit googleCalendar = new GoogleCalendarLimit();
	private GoogleTasksLimit googleTasks = new GoogleTasksLimit();
	private FastApiLimit fastapi = new FastApiLimit();
	private GoogleOAuthLimit googleOauth = new GoogleOAuthLimit();
	private AuthTokenLimit authToken = new AuthTokenLimit();
	private FilePresignedLimit filePresigned = new FilePresignedLimit();
	private FileAttachmentLimit fileAttachment = new FileAttachmentLimit();
	private BoardWriteLimit boardWrite = new BoardWriteLimit();
	private SocialActionLimit socialAction = new SocialActionLimit();

	@Getter
	@Setter
	public static class Redis {
		private String keyPrefix = "ratelimit";
	}

	@Getter
	@Setter
	public static class GoogleCalendarLimit {
		private int bucketCapacity = 100;
		private boolean enabled = true;
	}

	@Getter
	@Setter
	public static class GoogleTasksLimit {
		private int bucketCapacity = 100;
		private boolean enabled = true;
	}

	@Getter
	@Setter
	public static class FastApiLimit {
		private int bucketCapacity = 10;
		private boolean enabled = true;
	}

	@Getter
	@Setter
	public static class GoogleOAuthLimit {
		private int bucketCapacity = 50;
		private boolean enabled = true;
	}

	@Getter
	@Setter
	public static class AuthTokenLimit {
		private int bucketCapacity = 100;
		private boolean enabled = true;
	}

	@Getter
	@Setter
	public static class FilePresignedLimit {
		private int bucketCapacity = 100;
		private boolean enabled = true;
	}

	@Getter
	@Setter
	public static class FileAttachmentLimit {
		private int bucketCapacity = 200;
		private boolean enabled = true;
	}

	@Getter
	@Setter
	public static class BoardWriteLimit {
		private int bucketCapacity = 300;
		private boolean enabled = true;
	}

	@Getter
	@Setter
	public static class SocialActionLimit {
		private int bucketCapacity = 200;
		private boolean enabled = true;
	}
}
