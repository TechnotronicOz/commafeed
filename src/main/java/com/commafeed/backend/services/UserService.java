package com.commafeed.backend.services;

import java.util.Calendar;
import java.util.Collection;
import java.util.UUID;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;

import com.commafeed.backend.dao.FeedCategoryDAO;
import com.commafeed.backend.dao.FeedEntryStatusDAO;
import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.dao.UserSettingsDAO;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserRole;
import com.commafeed.backend.model.UserRole.Role;
import com.google.common.base.Preconditions;

@Stateless
public class UserService {

	@Inject
	UserDAO userDAO;

	@Inject
	FeedEntryStatusDAO feedEntryStatusDAO;

	@Inject
	FeedCategoryDAO feedCategoryDAO;

	@Inject
	UserSettingsDAO userSettingsDAO;

	@Inject
	PasswordEncryptionService encryptionService;

	@Inject
	ApplicationSettingsService applicationSettingsService;

	public User login(String name, String password) {
		if (name == null || password == null) {
			return null;
		}

		User user = userDAO.findByName(name);
		if (user != null && !user.isDisabled()) {
			boolean authenticated = encryptionService.authenticate(password,
					user.getPassword(), user.getSalt());
			if (authenticated) {
				user.setLastLogin(Calendar.getInstance().getTime());
				userDAO.saveOrUpdate(user);
				return user;
			}
		}

		return null;
	}

	public User register(String name, String password, String email,
			Collection<Role> roles) {
		return register(name, password, email, roles, false);
	}

	public User register(String name, String password, String email,
			Collection<Role> roles, boolean forceRegistration) {

		Preconditions.checkState(forceRegistration
				|| applicationSettingsService.get().isAllowRegistrations(),
				"Registrations are closed on this CommaFeed instance");
		Preconditions.checkNotNull(name);
		Preconditions.checkNotNull(email);
		Preconditions.checkNotNull(password);

		Preconditions.checkArgument(StringUtils.length(name) >= 3,
				"Name too short (3 characters minimum)");
		Preconditions.checkArgument(StringUtils.length(name) <= 32,
				"Name too long (32 characters maximum)");
		Preconditions.checkArgument(
				forceRegistration || StringUtils.length(password) >= 6,
				"Password too short (6 characters maximum)");
		Preconditions.checkArgument(StringUtils.contains(email, "@"),
				"Invalid email address");
		Preconditions.checkArgument(userDAO.findByName(name) == null,
				"Name already taken");
		Preconditions.checkArgument(userDAO.findByEmail(email) == null,
				"Email already taken");

		User user = new User();
		byte[] salt = encryptionService.generateSalt();
		user.setName(name);
		user.setEmail(email);
		user.setCreated(Calendar.getInstance().getTime());
		user.setSalt(salt);
		user.setPassword(encryptionService.getEncryptedPassword(password, salt));
		for (Role role : roles) {
			user.getRoles().add(new UserRole(user, role));
		}
		userDAO.saveOrUpdate(user);
		return user;
	}

	public void unregister(User user) {
		feedCategoryDAO.delete(feedCategoryDAO.findAll(user));
		userSettingsDAO.delete(userSettingsDAO.findByUser(user));
		userDAO.delete(user);
	}

	public String generateApiKey(User user) {
		byte[] key = encryptionService.getEncryptedPassword(UUID.randomUUID()
				.toString(), user.getSalt());
		return DigestUtils.sha1Hex(key);
	}
}
